/*
 * Copyright (c) 2002-2018 "Neo4j,"
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;

import org.neo4j.cursor.RawCursor;
import org.neo4j.index.internal.gbptree.Hit;

public class NativeDistinctValuesProgressor<KEY extends NativeIndexKey<KEY>, VALUE extends NativeIndexValue> extends NativeIndexProgressor<KEY,VALUE>
{
    private final IndexLayout<KEY,VALUE> layout;
    private final KEY prev;
    private boolean first = true;
    private long countForCurrentValue;
    private boolean last;

    NativeDistinctValuesProgressor( RawCursor<Hit<KEY,VALUE>,IOException> seeker, NodeValueClient client,
            Collection<RawCursor<Hit<KEY,VALUE>,IOException>> toRemoveFromOnClose, IndexLayout<KEY,VALUE> layout )
    {
        super( seeker, client, toRemoveFromOnClose );
        this.layout = layout;
        prev = layout.newKey();
    }

    @Override
    public boolean next()
    {
        try
        {
            while ( seeker.next() )
            {
                KEY key = seeker.get().key();
                try
                {
                    if ( first )
                    {
                        first = false;
                        countForCurrentValue = 1;
                    }
                    else if ( layout.compareValue( prev, key ) == 0 )
                    {
                        // same as previous
                        countForCurrentValue++;
                    }
                    else
                    {
                        // different from previous
                        boolean accepted = client.acceptNode( countForCurrentValue, extractValues( prev ) );
                        countForCurrentValue = 1;
                        if ( accepted )
                        {
                            return true;
                        }
                    }
                }
                finally
                {
                    layout.copyKey( key, prev );
                }
            }
            boolean finalResult = !first && !last && client.acceptNode( countForCurrentValue, extractValues( prev ) );
            last = true;
            return finalResult;
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }
}
