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
package org.neo4j.values.virtual;


import java.util.Comparator;

import org.neo4j.values.AnyValue;
import org.neo4j.values.AnyValueWriter;
import org.neo4j.values.ValueMapper;
import org.neo4j.values.VirtualValue;
import org.neo4j.values.utils.InvalidValuesArgumentException;

/**
 * The ErrorValue allow delaying errors in value creation until runtime, which is useful
 * if it turns out that the value is never used.
 */
public final class ErrorValue extends VirtualValue
{
    private final InvalidValuesArgumentException e;

    ErrorValue( Exception e )
    {
        this.e = new InvalidValuesArgumentException( e.getMessage() );
    }

    @Override
    public boolean equals( VirtualValue other )
    {
        throw e;
    }

    @Override
    public VirtualValueGroup valueGroup()
    {
        return VirtualValueGroup.ERROR;
    }

    @Override
    public int compareTo( VirtualValue other, Comparator<AnyValue> comparator )
    {
        throw e;
    }

    @Override
    protected int computeHash()
    {
        throw e;
    }

    @Override
    public <E extends Exception> void writeTo( AnyValueWriter<E> writer )
    {
        throw e;
    }

    @Override
    public <T> T map( ValueMapper<T> mapper )
    {
        throw e;
    }

    @Override
    public String getTypeName()
    {
        return "Error";
    }
}
