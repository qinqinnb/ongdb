/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.index.schema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.helpers.collection.BoundedIterable;
import org.neo4j.helpers.collection.CombiningIterable;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.IOLimiter;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.api.index.IndexPopulator;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.impl.annotations.ReporterFactory;
import org.neo4j.kernel.impl.api.index.IndexUpdateMode;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.kernel.impl.index.schema.fusion.FusionIndexBase;
import org.neo4j.storageengine.api.NodePropertyAccessor;
import org.neo4j.storageengine.api.schema.IndexDescriptor;
import org.neo4j.storageengine.api.schema.IndexReader;
import org.neo4j.storageengine.api.schema.StoreIndexDescriptor;

import static org.neo4j.helpers.collection.Iterators.concatResourceIterators;
import static org.neo4j.index.internal.gbptree.GBPTree.NO_HEADER_WRITER;
import static org.neo4j.kernel.impl.index.schema.fusion.FusionIndexBase.forAll;

class TemporalIndexAccessor extends TemporalIndexCache<TemporalIndexAccessor.PartAccessor<?>> implements IndexAccessor
{
    private final IndexDescriptor descriptor;

    TemporalIndexAccessor( StoreIndexDescriptor descriptor, IndexSamplingConfig samplingConfig, PageCache pageCache, FileSystemAbstraction fs,
            RecoveryCleanupWorkCollector recoveryCleanupWorkCollector, IndexProvider.Monitor monitor, TemporalIndexFiles temporalIndexFiles, boolean readOnly )
            throws IOException
    {
        super( new PartFactory( pageCache, fs, recoveryCleanupWorkCollector, monitor, descriptor, samplingConfig, temporalIndexFiles, readOnly ) );
        this.descriptor = descriptor;

        temporalIndexFiles.loadExistingIndexes( this );
    }

    @Override
    public void drop()
    {
        forAll( NativeIndexAccessor::drop, this );
    }

    @Override
    public IndexUpdater newUpdater( IndexUpdateMode mode )
    {
        return new TemporalIndexUpdater( this, mode );
    }

    @Override
    public void force( IOLimiter ioLimiter )
    {
        for ( NativeIndexAccessor part : this )
        {
            part.force( ioLimiter );
        }
    }

    @Override
    public void refresh()
    {
        // not required in this implementation
    }

    @Override
    public void close()
    {
        closeInstantiateCloseLock();
        forAll( NativeIndexAccessor::close, this );
    }

    @Override
    public IndexReader newReader()
    {
        return new TemporalIndexReader( descriptor, this );
    }

    @Override
    public BoundedIterable<Long> newAllEntriesReader()
    {
        ArrayList<BoundedIterable<Long>> allEntriesReader = new ArrayList<>();
        for ( NativeIndexAccessor<?,?> part : this )
        {
            allEntriesReader.add( part.newAllEntriesReader() );
        }

        return new BoundedIterable<Long>()
        {
            @Override
            public long maxCount()
            {
                long sum = 0L;
                for ( BoundedIterable<Long> part : allEntriesReader )
                {
                    long partMaxCount = part.maxCount();
                    if ( partMaxCount == UNKNOWN_MAX_COUNT )
                    {
                        return UNKNOWN_MAX_COUNT;
                    }
                    sum += partMaxCount;
                }
                return sum;
            }

            @Override
            public void close() throws Exception
            {
                forAll( BoundedIterable::close, allEntriesReader );
            }

            @Override
            public Iterator<Long> iterator()
            {
                return new CombiningIterable<>( allEntriesReader ).iterator();
            }
        };
    }

    @Override
    public ResourceIterator<File> snapshotFiles()
    {
        List<ResourceIterator<File>> snapshotFiles = new ArrayList<>();
        for ( NativeIndexAccessor<?,?> part : this )
        {
            snapshotFiles.add( part.snapshotFiles() );
        }
        return concatResourceIterators( snapshotFiles.iterator() );
    }

    @Override
    public void verifyDeferredConstraints( NodePropertyAccessor nodePropertyAccessor )
    {
        // Not needed since uniqueness is verified automatically w/o cost for every update.
    }

    @Override
    public boolean isDirty()
    {
        return Iterators.stream( iterator() ).anyMatch( NativeIndexAccessor::isDirty );
    }

    @Override
    public boolean consistencyCheck( ReporterFactory reporterFactory )
    {
        return FusionIndexBase.consistencyCheck( this, reporterFactory );
    }

    static class PartAccessor<KEY extends NativeIndexSingleValueKey<KEY>> extends NativeIndexAccessor<KEY,NativeIndexValue>
    {
        private final IndexLayout<KEY,NativeIndexValue> layout;
        private final IndexDescriptor descriptor;

        PartAccessor( PageCache pageCache, FileSystemAbstraction fs, TemporalIndexFiles.FileLayout<KEY> fileLayout,
                RecoveryCleanupWorkCollector recoveryCleanupWorkCollector, IndexProvider.Monitor monitor, StoreIndexDescriptor descriptor, boolean readOnly )
        {
            super( pageCache, fs, fileLayout.indexFile, fileLayout.layout, monitor, descriptor, NO_HEADER_WRITER, readOnly );
            this.layout = fileLayout.layout;
            this.descriptor = descriptor;
            instantiateTree( recoveryCleanupWorkCollector, headerWriter );
        }

        @Override
        public TemporalIndexPartReader<KEY> newReader()
        {
            assertOpen();
            return new TemporalIndexPartReader<>( tree, layout, descriptor );
        }
    }

    static class PartFactory implements TemporalIndexCache.Factory<PartAccessor<?>>
    {
        private final PageCache pageCache;
        private final FileSystemAbstraction fs;
        private final RecoveryCleanupWorkCollector recoveryCleanupWorkCollector;
        private final IndexProvider.Monitor monitor;
        private final StoreIndexDescriptor descriptor;
        private final IndexSamplingConfig samplingConfig;
        private final TemporalIndexFiles temporalIndexFiles;
        private final boolean readOnly;

        PartFactory( PageCache pageCache, FileSystemAbstraction fs, RecoveryCleanupWorkCollector recoveryCleanupWorkCollector, IndexProvider.Monitor monitor,
                StoreIndexDescriptor descriptor, IndexSamplingConfig samplingConfig, TemporalIndexFiles temporalIndexFiles, boolean readOnly )
        {
            this.pageCache = pageCache;
            this.fs = fs;
            this.recoveryCleanupWorkCollector = recoveryCleanupWorkCollector;
            this.monitor = monitor;
            this.descriptor = descriptor;
            this.samplingConfig = samplingConfig;
            this.temporalIndexFiles = temporalIndexFiles;
            this.readOnly = readOnly;
        }

        @Override
        public PartAccessor<?> newDate()
        {
            return createPartAccessor( temporalIndexFiles.date() );
        }

        @Override
        public PartAccessor<?> newLocalDateTime()
        {
            return createPartAccessor( temporalIndexFiles.localDateTime() );
        }

        @Override
        public PartAccessor<?> newZonedDateTime()
        {
            return createPartAccessor( temporalIndexFiles.zonedDateTime() );
        }

        @Override
        public PartAccessor<?> newLocalTime()
        {
            return createPartAccessor( temporalIndexFiles.localTime() );
        }

        @Override
        public PartAccessor<?> newZonedTime()
        {
            return createPartAccessor( temporalIndexFiles.zonedTime() );
        }

        @Override
        public PartAccessor<?> newDuration()
        {
            return createPartAccessor( temporalIndexFiles.duration() );
        }

        private <KEY extends NativeIndexSingleValueKey<KEY>> PartAccessor<KEY> createPartAccessor( TemporalIndexFiles.FileLayout<KEY> fileLayout )
        {
            if ( !fs.fileExists( fileLayout.indexFile ) )
            {
                createEmptyIndex( fileLayout );
            }
            return new PartAccessor<>( pageCache, fs, fileLayout, recoveryCleanupWorkCollector, monitor, descriptor, readOnly );
        }

        private <KEY extends NativeIndexSingleValueKey<KEY>> void createEmptyIndex( TemporalIndexFiles.FileLayout<KEY> fileLayout )
        {
            IndexPopulator populator = new TemporalIndexPopulator.PartPopulator<>( pageCache, fs, fileLayout, monitor, descriptor );
            populator.create();
            populator.close( true );
        }
    }
}
