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
package org.neo4j.kernel.api.schema.index;

import org.neo4j.kernel.api.schema.SchemaDescriptorFactory;
import org.neo4j.storageengine.api.schema.IndexDescriptor;
import org.neo4j.storageengine.api.schema.IndexDescriptorFactory;

public class TestIndexDescriptorFactory
{
    private TestIndexDescriptorFactory()
    {
    }

    public static IndexDescriptor forLabel( int labelId, int... propertyIds )
    {
        return IndexDescriptorFactory.forSchema( SchemaDescriptorFactory.forLabel( labelId, propertyIds ) );
    }

    public static IndexDescriptor uniqueForLabel( int labelId, int... propertyIds )
    {
        return IndexDescriptorFactory.uniqueForSchema( SchemaDescriptorFactory.forLabel( labelId, propertyIds ) );
    }
}
