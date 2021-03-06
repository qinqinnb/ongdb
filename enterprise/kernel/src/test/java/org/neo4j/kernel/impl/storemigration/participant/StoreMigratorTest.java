/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) with the
 * Commons Clause, as found in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * Neo4j object code can be licensed independently from the source
 * under separate terms from the AGPL. Inquiries can be directed to:
 * licensing@neo4j.com
 *
 * More information is also available at:
 * https://neo4j.com/licensing/
 */
package org.neo4j.kernel.impl.storemigration.participant;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.tracing.cursor.PageCursorTracerSupplier;
import org.neo4j.io.pagecache.tracing.cursor.context.EmptyVersionContextSupplier;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.pagecache.ConfiguringPageCacheFactory;
import org.neo4j.kernel.impl.store.format.StoreVersion;
import org.neo4j.kernel.impl.store.format.highlimit.v300.HighLimitV3_0_0;
import org.neo4j.kernel.impl.storemigration.StoreVersionCheck;
import org.neo4j.kernel.impl.storemigration.StoreVersionCheck.Result;
import org.neo4j.kernel.impl.util.monitoring.ProgressReporter;
import org.neo4j.logging.NullLog;
import org.neo4j.logging.internal.NullLogService;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.scheduler.ThreadPoolJobScheduler;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.neo4j.test.rule.TestDirectory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.neo4j.graphdb.factory.GraphDatabaseSettings.pagecache_memory;
import static org.neo4j.io.pagecache.tracing.PageCacheTracer.NULL;

public class StoreMigratorTest
{
    @Rule
    public final TestDirectory directory = TestDirectory.testDirectory();

    @Test
    public void shouldNotDoActualStoreMigrationBetween3_0_5_and_next() throws Exception
    {
        // GIVEN a store in vE.H.0 format
        DatabaseLayout databaseLayout = directory.databaseLayout();
        new TestGraphDatabaseFactory().newEmbeddedDatabaseBuilder( databaseLayout.databaseDirectory() )
                // The format should be vE.H.0, HighLimit.NAME may point to a different version in future versions
                .setConfig( GraphDatabaseSettings.record_format, HighLimitV3_0_0.NAME )
                .newGraphDatabase()
                .shutdown();
        Config config = Config.defaults( pagecache_memory, "8m" );

        try ( FileSystemAbstraction fs = new DefaultFileSystemAbstraction();
              JobScheduler jobScheduler = new ThreadPoolJobScheduler();
              PageCache pageCache = new ConfiguringPageCacheFactory( fs, config, NULL,
                      PageCursorTracerSupplier.NULL, NullLog.getInstance(), EmptyVersionContextSupplier.EMPTY, jobScheduler )
                     .getOrCreatePageCache() )
        {
            // For test code sanity
            String fromStoreVersion = StoreVersion.HIGH_LIMIT_V3_0_0.versionString();
            Result hasVersionResult = new StoreVersionCheck( pageCache ).hasVersion(
                    databaseLayout.metadataStore(), fromStoreVersion );
            assertTrue( hasVersionResult.actualVersion, hasVersionResult.outcome.isSuccessful() );

            // WHEN
            StoreMigrator migrator = new StoreMigrator( fs, pageCache, config, NullLogService.getInstance(), jobScheduler );
            ProgressReporter monitor = mock( ProgressReporter.class );
            DatabaseLayout migrationLayout = directory.databaseLayout( "migration" );
            migrator.migrate( directory.databaseLayout(), migrationLayout, monitor, fromStoreVersion,
                    StoreVersion.HIGH_LIMIT_V3_0_6.versionString() );

            // THEN
            verifyNoMoreInteractions( monitor );
        }
    }

    @Test
    public void detectObsoleteCountStoresToRebuildDuringMigration()
    {
        FileSystemAbstraction fileSystem = new DefaultFileSystemAbstraction();
        PageCache pageCache = mock( PageCache.class );
        Config config = Config.defaults();
        CountsMigrator storeMigrator = new CountsMigrator( fileSystem, pageCache, config );
        Set<String> actualVersions = new HashSet<>();
        Set<String> expectedVersions = Arrays.stream( StoreVersion.values() ).map( StoreVersion::versionString )
                        .collect( Collectors.toSet() );

        assertTrue( CountsMigrator.countStoreRebuildRequired( StoreVersion.STANDARD_V2_3.versionString() ) );
        actualVersions.add( StoreVersion.STANDARD_V2_3.versionString() );
        assertTrue( CountsMigrator.countStoreRebuildRequired( StoreVersion.STANDARD_V3_0.versionString() ) );
        actualVersions.add( StoreVersion.STANDARD_V3_0.versionString() );
        assertFalse( CountsMigrator.countStoreRebuildRequired( StoreVersion.STANDARD_V3_2.versionString() ) );
        actualVersions.add( StoreVersion.STANDARD_V3_2.versionString() );
        assertFalse( CountsMigrator.countStoreRebuildRequired( StoreVersion.STANDARD_V3_4.versionString() ) );
        actualVersions.add( StoreVersion.STANDARD_V3_4.versionString() );
        assertFalse( CountsMigrator.countStoreRebuildRequired( StoreVersion.STANDARD_V3_6.versionString() ) );
        actualVersions.add( StoreVersion.STANDARD_V3_6.versionString() );

        assertTrue( CountsMigrator.countStoreRebuildRequired( StoreVersion.HIGH_LIMIT_V3_0_0.versionString() ) );
        actualVersions.add( StoreVersion.HIGH_LIMIT_V3_0_0.versionString() );
        assertTrue( CountsMigrator.countStoreRebuildRequired( StoreVersion.HIGH_LIMIT_V3_0_6.versionString() ) );
        actualVersions.add( StoreVersion.HIGH_LIMIT_V3_0_6.versionString() );
        assertTrue( CountsMigrator.countStoreRebuildRequired( StoreVersion.HIGH_LIMIT_V3_1_0.versionString() ) );
        actualVersions.add( StoreVersion.HIGH_LIMIT_V3_1_0.versionString() );
        assertFalse( CountsMigrator.countStoreRebuildRequired( StoreVersion.HIGH_LIMIT_V3_2_0.versionString() ) );
        actualVersions.add( StoreVersion.HIGH_LIMIT_V3_2_0.versionString() );
        assertFalse( CountsMigrator.countStoreRebuildRequired( StoreVersion.HIGH_LIMIT_V3_4_0.versionString() ) );
        actualVersions.add( StoreVersion.HIGH_LIMIT_V3_4_0.versionString() );
        assertFalse( CountsMigrator.countStoreRebuildRequired( StoreVersion.HIGH_LIMIT_V3_6_0.versionString() ) );
        actualVersions.add( StoreVersion.HIGH_LIMIT_V3_6_0.versionString() );

        assertEquals( expectedVersions, actualVersions );
    }

}
