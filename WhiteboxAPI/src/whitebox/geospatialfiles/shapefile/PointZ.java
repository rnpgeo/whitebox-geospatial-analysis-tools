/*
 * Copyright (C) 2012 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
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
package whitebox.geospatialfiles.shapefile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import whitebox.structures.BoundingBox;
/**
 *
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class PointZ implements Geometry {
    private double x;
    private double y;
    private double z;
    private double m;

    //constructors
    public PointZ(byte[] rawData) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(rawData);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.rewind();
            x = buf.getDouble(0);
            y = buf.getDouble(8);
            z = buf.getDouble(16);
            m = buf.getDouble(24);
            buf.clear();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
    
    public PointZ(double x, double y, double z, double m) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.m = m;
    }
    
    
    public PointZ(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.m = 0;
    }
    
    // properties

    public double getM() {
        return m;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
    
    @Override
    public int getLength() {
        return 32;
    }
    
    @Override
    public ByteBuffer toByteBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.rewind();
        buf.putDouble(x);
        buf.putDouble(y);
        buf.putDouble(z);
        buf.putDouble(m);
        return buf;
    }

    @Override
    public ShapeType getShapeType() {
        return ShapeType.POINTZ;
    }
    
    @Override
    public boolean isMappable(BoundingBox box, double minSize) {
        if (box.isPointInBox(x, y)) {
            return true;
        } else {
            return false;
        }
    }
}