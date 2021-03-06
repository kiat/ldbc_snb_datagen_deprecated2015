/*
 * Copyright (c) 2013 LDBC
 * Linked Data Benchmark Council (http://ldbc.eu)
 *
 * This file is part of ldbc_socialnet_dbgen.
 *
 * ldbc_socialnet_dbgen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ldbc_socialnet_dbgen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ldbc_socialnet_dbgen.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2011 OpenLink Software <bdsmt@openlinksw.com>
 * All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation;  only Version 2 of the License dated
 * June 1991.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package ldbc.socialnet.dbgen.objects;

import java.util.TreeSet;

public class Photo extends Message {
    int locationIdx;
    String locationName;
    double latt; 
    double longt;

    /**
     * < @brief The timestamps when the interested users where actually interested.
     *
     * @param messageId
     * @param content
     * @param textSize
     * @param creationDate
     * @param authorId
     * @param groupId
     * @param tags
     * @param ipAddress
     * @param userAgent
     * @param browserIdx
     */
    public Photo(long messageId,
                 String content,
                 int textSize,
                 long creationDate,
                 long authorId,
                 long authorCreationDate,
                 long groupId,
                 TreeSet<Integer> tags,
                 IP ipAddress,
                 String userAgent,
                 byte browserIdx,
                 int locationId,
                 String locationName,
                 double latt,
                 double longt
                 ) {
        super(messageId, content, textSize, creationDate, authorId,authorCreationDate, groupId, tags, ipAddress, userAgent, browserIdx, locationId);
        this.locationName = locationName;
        this.latt = latt;
        this.longt = longt;
    }

    public double getLatt() {
        return latt;
    }
    public void setLatt(double latt) {
        this.latt = latt;
    }
    public double getLongt() {
        return longt;
    }
    public void setLongt(double longt) {
        this.longt = longt;
    }
    public String getLocationName() {
        return locationName;
    }
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
}
