/*
 * Copyright (C) 2011-2012 Dr. John Lindsay <jlindsay@uoguelph.ca>
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

package plugins;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import java.io.File;
import java.util.ArrayList;
import whitebox.geospatialfiles.ShapeFile;
import whitebox.geospatialfiles.shapefile.attributes.DBFField;
import whitebox.geospatialfiles.shapefile.attributes.DBFReader;
import whitebox.geospatialfiles.shapefile.attributes.DBFWriter;
import whitebox.geospatialfiles.shapefile.PointsList;
import whitebox.geospatialfiles.shapefile.ShapeFileRecord;
import whitebox.geospatialfiles.shapefile.ShapeType;
import whitebox.geospatialfiles.shapefile.ShapefilePoint;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;
import whitebox.utilities.Topology;

/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 *
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class SymmetricDifference implements WhiteboxPlugin {
    
    private WhiteboxPluginHost myHost = null;
    private String[] args;
    
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name
     * containing no spaces.
     *
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "SymmetricDifference";
    }

    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer
     * name (containing spaces) and is used in the interface to list the tool.
     *
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Symmetric Difference";
    }

    /**
     * Used to retrieve a short description of what the plugin tool does.
     *
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Computes a geometric symmetric difference of vector features";
    }

    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     *
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "OverlayTools" };
    	return ret;
    }

    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the
     * class that the plugin will send all feedback messages, progress updates,
     * and return objects.
     *
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }

    /**
     * Used to communicate feedback pop-up messages between a plugin tool and
     * the main Whitebox user-interface.
     *
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }

    /**
     * Used to communicate a return object from a plugin tool to the main
     * Whitebox user-interface.
     *
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";
  
    /**
     * Used to communicate a progress update between a plugin tool and the main
     * Whitebox user interface.
     *
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }

    /**
     * Used to communicate a progress update between a plugin tool and the main
     * Whitebox user interface.
     *
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
    }
    
    /**
     * Sets the arguments (parameters) used by the plugin.
     *
     * @param args
     */
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
   
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     *
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
   
    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     *
     * @return a boolean describing whether or not the plugin is actively being
     * used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }

    @Override
    public void run() {
        
        amIActive = true;
        String[] shapefiles = new String[2];
        String outputFile;
        int progress;
        int i, n, FID;
        int oneHundredthTotal;
        int numRecs;
        ShapeType shapeType;
        ShapeType outputShapeType = ShapeType.POLYGON;
        GeometryFactory factory = new GeometryFactory();
        com.vividsolutions.jts.geom.Geometry g1 = null;
        com.vividsolutions.jts.geom.Geometry g2 = null;
            
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        shapefiles[0] = args[0];
        shapefiles[1] = args[1];
        outputFile = args[2];
        
        // check to see that the inputHeader and outputHeader are not null.
        if ((shapefiles[0].length() <= 0) || (shapefiles[1].length() <= 0) || (outputFile == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }
        
        try {
            
            com.vividsolutions.jts.geom.Geometry[] recJTSGeometries = null;
            ArrayList<com.vividsolutions.jts.geom.Geometry> inputGeometryList =
                    new ArrayList<com.vividsolutions.jts.geom.Geometry>();
            com.vividsolutions.jts.geom.Geometry outputGeometry = null;
                
            
            ShapeFile input1 = new ShapeFile(shapefiles[0]);
            shapeType = input1.getShapeType();
            numRecs = input1.getNumberOfRecords();

            if (shapeType.getBaseType() == ShapeType.POINT
                    || shapeType.getBaseType() == ShapeType.MULTIPOINT) {
                outputShapeType = ShapeType.POINT;
            } else if (shapeType.getBaseType() == ShapeType.POLYLINE
                    && outputShapeType == ShapeType.POLYGON) {
                outputShapeType = ShapeType.POLYLINE;
            }

            oneHundredthTotal = numRecs / 100;
            progress = 0;
            n = 0;
            for (ShapeFileRecord record : input1.records) {
                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                    recJTSGeometries = record.getGeometry().getJTSGeometries();
                    for (int a = 0; a < recJTSGeometries.length; a++) {
                        recJTSGeometries[a].setUserData(record.getRecordNumber());
                        if (recJTSGeometries[a].isValid()) {
                            inputGeometryList.add(recJTSGeometries[a]);
                        } else {
                            System.out.println(record.getRecordNumber() + " is invalid.");
                        }
                    }
                }
                n++;
                if (n >= oneHundredthTotal) {
                    n = 0;
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                    progress++;
                    updateProgress("Loop 1 of " + 2 + ": Reading data:", progress);
                }
            }
            g1 = factory.buildGeometry(inputGeometryList);
            inputGeometryList.clear();


            
            ShapeFile input2 = new ShapeFile(shapefiles[1]);
            shapeType = input2.getShapeType();
            numRecs = input2.getNumberOfRecords();

            if (shapeType.getBaseType() == ShapeType.POINT
                    || shapeType.getBaseType() == ShapeType.MULTIPOINT) {
                outputShapeType = ShapeType.POINT;
            } else if (shapeType.getBaseType() == ShapeType.POLYLINE
                    && outputShapeType == ShapeType.POLYGON) {
                outputShapeType = ShapeType.POLYLINE;
            }

            oneHundredthTotal = numRecs / 100;
            progress = 0;
            n = 0;
            for (ShapeFileRecord record : input2.records) {
                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                    recJTSGeometries = record.getGeometry().getJTSGeometries();
                    for (int a = 0; a < recJTSGeometries.length; a++) {
                        recJTSGeometries[a].setUserData(record.getRecordNumber());
                        if (recJTSGeometries[a].isValid()) {
                            inputGeometryList.add(recJTSGeometries[a]);
                        } else {
                            System.out.println(record.getRecordNumber() + " is invalid.");
                        }
                    }
                }
                n++;
                if (n >= oneHundredthTotal) {
                    n = 0;
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                    progress++;
                    updateProgress("Loop 2 of " + 2 + ": Reading data:", progress);
                }
            }
            
            g2 = factory.buildGeometry(inputGeometryList);
            inputGeometryList.clear();
            
            
            
            updateProgress("Performing operation (progress will not be updated):", -1);
            try {
                outputGeometry = g1.symDifference(g2);
            } catch (Exception ex) {
                outputGeometry = com.vividsolutions.jts.operation.overlay.snap.SnapOverlayOp.symDifference(g1, g2);
            }


            ShapeFile output = null;
            DBFWriter writer = null;
            DBFReader reader1 = null;
            DBFReader reader2 = null;

            reader1 = new DBFReader(input1.getDatabaseFile());
            
            // set up the output files of the shapefile and the dbf
            output = new ShapeFile(outputFile, outputShapeType);

            //int numFields1 = reader1.getFieldCount();
            //int numFields2 = reader2.getFieldCount();
            int numFields = 3; //1 + numFields1 + numFields2;
            
            DBFField fields[] = new DBFField[numFields];

            fields[0] = new DBFField();
            fields[0].setName("FID");
            fields[0].setDataType(DBFField.FIELD_TYPE_N);
            fields[0].setFieldLength(10);
            fields[0].setDecimalCount(0);

            fields[1] = new DBFField();
            fields[1].setName("PARENT1_ID");
            fields[1].setDataType(DBFField.FIELD_TYPE_N);
            fields[1].setFieldLength(10);
            fields[1].setDecimalCount(0);

            fields[2] = new DBFField();
            fields[2].setName("PARENT2_ID");
            fields[2].setDataType(DBFField.FIELD_TYPE_N);
            fields[2].setFieldLength(10);
            fields[2].setDecimalCount(0);
            
//            for (int a = 0; a < numFields1; a++) {
//                DBFField inputField = reader1.getField(a);
//                fields[a + 1] = inputField;
//                if (inputField.getName().equals("FID")) {
//                    fields[a + 1].setName("PARENT1_FID");                    
//                }
//            }
//
//            for (int a = 0; a < numFields2; a++) {
//                DBFField inputField = reader2.getField(a);
//                fields[a + 1 + numFields1] = inputField;
//                if (inputField.getName().equals("FID")) {
//                    fields[a + 1 + numFields1].setName("PARENT2_FID");                    
//                }
//            }
            
            String DBFName = output.getDatabaseFile();
            writer = new DBFWriter(new File(DBFName));

            writer.setFields(fields);
            
            PreparedGeometry[] tests1 = new PreparedGeometry[g1.getNumGeometries()];
            com.vividsolutions.jts.geom.Geometry[] testGs1 = new com.vividsolutions.jts.geom.Geometry[g1.getNumGeometries()];
            int[] userData1 = new int[g1.getNumGeometries()];
            for (int a = 0; a < g1.getNumGeometries(); a++) {
                tests1[a] = PreparedGeometryFactory.prepare(g1.getGeometryN(a));
                userData1[a] = Integer.parseInt(g1.getGeometryN(a).getUserData().toString());
                testGs1[a] = g1.getGeometryN(a);

            }

            PreparedGeometry[] tests2 = new PreparedGeometry[g2.getNumGeometries()];
            com.vividsolutions.jts.geom.Geometry[] testGs2 = new com.vividsolutions.jts.geom.Geometry[g2.getNumGeometries()];
            int[] userData2 = new int[g2.getNumGeometries()];
            for (int a = 0; a < g2.getNumGeometries(); a++) {
                tests2[a] = PreparedGeometryFactory.prepare(g2.getGeometryN(a));
                userData2[a] = Integer.parseInt(g2.getGeometryN(a).getUserData().toString());
                testGs2[a] = g2.getGeometryN(a);
            }
            
//            Object[][] attributeTableRecords = new Object[reader1.getRecordCount()][numFields];           
//            for (int a = 0; a < reader1.getRecordCount(); a++) {
//                Object[] rec = reader1.nextRecord();
//                for (int b = 0; b < numFields1 - 1; b++) {
//                    attributeTableRecords[a][b + 1] = rec[b];
//                }
//            }
//            
//            for (int a = 0; a < reader2.getRecordCount(); a++) {
//                Object[] rec = reader2.nextRecord();
//                for (int b = 0; b < numFields2 - 1; b++) {
//                    attributeTableRecords[a][b + 1 + numFields1] = rec[b];
//                }
//            }
                
            if (outputGeometry instanceof GeometryCollection) {
                int numGeometries = outputGeometry.getNumGeometries();
                oneHundredthTotal = (int)(numGeometries / 100.0);
                progress = 0;
                n = 0;
                FID = 0;
                int parentRecNum1 = 0;
                int parentRecNum2 = 0;
                for (int a = 0; a < numGeometries; a++) {
                    parentRecNum1 = -999;
                    parentRecNum2 = -999;
                    com.vividsolutions.jts.geom.Geometry gN = outputGeometry.getGeometryN(a);

                    if (gN instanceof com.vividsolutions.jts.geom.Point
                            && outputShapeType == ShapeType.POINT) {
                        Coordinate p = gN.getCoordinate();
                        for (int m = 0; m < tests1.length; m++) {
                            if (tests1[m].overlaps(gN) || gN.distance(testGs1[m]) < 0.0001) {
                                parentRecNum1 = userData1[m];
                                break;
                            }
                        }
                        for (int m = 0; m < tests2.length; m++) {
                            if (tests2[m].overlaps(gN) || gN.distance(testGs2[m]) < 0.0001) {
                                parentRecNum2 = userData2[m];
                                break;
                            }
                        }
                        // you will loose any z and m information if they are in the input file.
                        // you'll need to fix this some other time when you get a chance.
                        whitebox.geospatialfiles.shapefile.Point wbGeometry = new whitebox.geospatialfiles.shapefile.Point(p.x, p.y);
                        output.addRecord(wbGeometry);

                        FID++;
                        Object[] rowData = new Object[numFields];
                        rowData[0] = new Double(FID);
                        rowData[1] = new Double(parentRecNum1);
                        rowData[2] = new Double(parentRecNum2);
                        writer.addRecord(rowData);
                    } else if (gN instanceof LineString
                            && outputShapeType == ShapeType.POLYLINE) {
                        for (int m = 0; m < tests1.length; m++) {
                            if (tests1[m].overlaps(gN) || gN.distance(testGs1[m]) < 0.0001) {
                                parentRecNum1 = userData1[m];
                                break;
                            }
                        }
                        for (int m = 0; m < tests2.length; m++) {
                            if (tests2[m].crosses(gN) || gN.distance(testGs2[m]) < 0.0001) {
                                parentRecNum2 = userData2[m];
                                break;
                            }
                        }
                        LineString ls = (LineString) gN;
                        ArrayList<ShapefilePoint> pnts = new ArrayList<ShapefilePoint>();

                        int[] parts = {0};

                        Coordinate[] coords = ls.getCoordinates();
                        for (i = 0; i < coords.length; i++) {
                            pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                        }

                        PointsList pl = new PointsList(pnts);
                        whitebox.geospatialfiles.shapefile.PolyLine wbGeometry = new whitebox.geospatialfiles.shapefile.PolyLine(parts, pl.getPointsArray());
                        output.addRecord(wbGeometry);

                        FID++;
                        Object[] rowData = new Object[numFields];
                        rowData[0] = new Double(FID);
                        if (parentRecNum1 != -999) { rowData[1] = new Double(parentRecNum1); }
                        if (parentRecNum2 != -999) { rowData[2] = new Double(parentRecNum2); }
                        writer.addRecord(rowData);
                    } else if (gN instanceof com.vividsolutions.jts.geom.Polygon 
                            && outputShapeType == ShapeType.POLYLINE) {
                        
                        for (int m = 0; m < tests1.length; m++) {
                            if (tests1[m].contains(gN.getInteriorPoint())) {
                                parentRecNum1 = userData1[m];
                                break;
                            }
                        }
                        for (int m = 0; m < tests2.length; m++) {
                            if (tests2[m].contains(gN.getInteriorPoint())) {
                                parentRecNum2 = userData2[m];
                                break;
                            }
                        }
                        com.vividsolutions.jts.geom.Polygon p = (com.vividsolutions.jts.geom.Polygon)gN;
                        ArrayList<ShapefilePoint> pnts = new ArrayList<ShapefilePoint>();

                        int[] parts = new int[p.getNumInteriorRing() + 1];

                        Coordinate[] coords = p.getExteriorRing().getCoordinates();
                        if (!Topology.isClockwisePolygon(coords)) {
                            for (i = coords.length - 1; i >= 0; i--) {
                                pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                            }
                        } else {
                            for (i = 0; i < coords.length; i++) {
                                pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                            }
                        }

                        for (int b = 0; b < p.getNumInteriorRing(); b++) {
                            parts[b + 1] = pnts.size();
                            coords = p.getInteriorRingN(b).getCoordinates();
                            if (Topology.isClockwisePolygon(coords)) {
                                for (i = coords.length - 1; i >= 0; i--) {
                                    pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                                }
                            } else {
                                for (i = 0; i < coords.length; i++) {
                                    pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                                }
                            }
                        }

                        PointsList pl = new PointsList(pnts);
                        whitebox.geospatialfiles.shapefile.PolyLine wbGeometry = new whitebox.geospatialfiles.shapefile.PolyLine(parts, pl.getPointsArray());
                        output.addRecord(wbGeometry);

                        FID++;
                        Object[] rowData = new Object[numFields];
                        rowData[0] = new Double(FID);
                        if (parentRecNum1 != -999) { rowData[1] = new Double(parentRecNum1); }
                        if (parentRecNum2 != -999) { rowData[2] = new Double(parentRecNum2); }
                        writer.addRecord(rowData);
                    } else if (gN instanceof com.vividsolutions.jts.geom.Polygon 
                            && outputShapeType == ShapeType.POLYGON) {
                        for (int m = 0; m < tests1.length; m++) {
                            if (tests1[m].contains(gN.getInteriorPoint())) {
                                parentRecNum1 = userData1[m];
                                break;
                            }
                        }
                        for (int m = 0; m < tests2.length; m++) {
                            if (tests2[m].contains(gN.getInteriorPoint())) {
                                parentRecNum2 = userData2[m];
                                break;
                            }
                        }
                        com.vividsolutions.jts.geom.Polygon p = (com.vividsolutions.jts.geom.Polygon) gN;
                        ArrayList<ShapefilePoint> pnts = new ArrayList<ShapefilePoint>();

                        int[] parts = new int[p.getNumInteriorRing() + 1];

                        Coordinate[] coords = p.getExteriorRing().getCoordinates();
                        if (!Topology.isClockwisePolygon(coords)) {
                            for (i = coords.length - 1; i >= 0; i--) {
                                pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                            }
                        } else {
                            for (i = 0; i < coords.length; i++) {
                                pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                            }
                        }

                        for (int b = 0; b < p.getNumInteriorRing(); b++) {
                            parts[b + 1] = pnts.size();
                            coords = p.getInteriorRingN(b).getCoordinates();
                            if (Topology.isClockwisePolygon(coords)) {
                                for (i = coords.length - 1; i >= 0; i--) {
                                    pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                                }
                            } else {
                                for (i = 0; i < coords.length; i++) {
                                    pnts.add(new ShapefilePoint(coords[i].x, coords[i].y));
                                }
                            }
                        }

                        PointsList pl = new PointsList(pnts);
                        whitebox.geospatialfiles.shapefile.Polygon wbGeometry = new whitebox.geospatialfiles.shapefile.Polygon(parts, pl.getPointsArray());
                        output.addRecord(wbGeometry);

                        FID++;
                        Object[] rowData = new Object[numFields];
                        rowData[0] = new Double(FID);
                        if (parentRecNum1 != -999) { rowData[1] = new Double(parentRecNum1); }
                        if (parentRecNum2 != -999) { rowData[2] = new Double(parentRecNum2); }
                        writer.addRecord(rowData);
                    } else {
                        // it shouldn't really hit here ever.
                        //showFeedback("An error was encountered when saving the output file.");
                        //return;
                    }
                    
                    n++;
                    if (n >= oneHundredthTotal) {
                        n = 0;
                        if (cancelOp) {
                            cancelOperation();
                            return;
                        }
                        progress++;
                        updateProgress("Saving output file", progress);
                    }
                }
            } else {
                // it shouldn't really hit here ever.
                showFeedback("An error was encountered when saving the output file.");
                return;
            }
            
            output.write();
            writer.write();
            
            
            // returning a header file string displays the image.
            returnData(outputFile);
            
        }  catch (Exception e) {
            showFeedback(e.getMessage());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
       
    }
    
//    // This method is only used during testing.
//    public static void main(String[] args) {
//        args = new String[3];
////        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/NTDB_roads_rmow.shp"
////                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/Water_Line_rmow.shp"
////                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/Water_Body_rmow.shp";
////        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp1.shp";
//
////        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/someLakes.shp"
////                + ";/Users/johnlindsay/Documents/Data/ShapeFiles/tmp5.shp";
////        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp1.shp";
//        
////        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/rondeau lakes.shp"
////                + ";/Users/johnlindsay/Documents/Research/Conference Presentations and Guest Talks/2012 CGU/Data/rivers.shp";
////        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp2.shp";
//        
//        args[0] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp1.shp";
//        args[1] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp2.shp";
//        args[2] = "/Users/johnlindsay/Documents/Data/ShapeFiles/tmp3.shp";
//        
//        SymmetricDifference sd = new SymmetricDifference();
//        sd.setArgs(args);
//        sd.run();
//    }
}