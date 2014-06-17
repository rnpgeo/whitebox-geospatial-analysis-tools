/*
 * Copyright (C) 2014 Dr. John Lindsay <jlindsay@uoguelph.ca>
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
 
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import java.util.concurrent.Future
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.Date
import java.util.ArrayList
import whitebox.interfaces.WhiteboxPluginHost
import whitebox.geospatialfiles.LASReader
import whitebox.geospatialfiles.LASReader.PointRecord
import whitebox.geospatialfiles.LASReader.PointRecColours
import whitebox.structures.BoundingBox
import whitebox.structures.KdTree
import whitebox.structures.BooleanBitArray1D
import whitebox.ui.plugin_dialog.ScriptDialog
import whitebox.utilities.StringUtilities
import whitebox.geospatialfiles.ShapeFile;
import whitebox.geospatialfiles.shapefile.*;
import whitebox.geospatialfiles.shapefile.attributes.DBFField;
import groovy.transform.CompileStatic

// The following four variables are required for this 
// script to be integrated into the tool tree panel. 
// Comment them out if you want to remove the script.
def name = "IsolateGroundPoints"
def descriptiveName = "Isolate Ground Points (LiDAR)"
def description = "Seperates ground points from off-terrain points."
def toolboxes = ["LidarTools"]

public class IsolateGroundPoints implements ActionListener {
	private WhiteboxPluginHost pluginHost
	private ScriptDialog sd;
	private String descriptiveName
	
	private AtomicInteger numSolvedTiles = new AtomicInteger(0)
	
	public IsolateGroundPoints(WhiteboxPluginHost pluginHost, 
		String[] args, def name, def descriptiveName) {
		this.pluginHost = pluginHost
		this.descriptiveName = descriptiveName
			
		if (args.length > 0) {
			execute(args)
		} else {
			// Create a dialog for this tool to collect user-specified
			// tool parameters.
			sd = new ScriptDialog(pluginHost, descriptiveName, this)	
		
			// Specifying the help file will display the html help
			// file in the help pane. This file should be be located 
			// in the help directory and have the same name as the 
			// class, with an html extension.
			sd.setHelpFile(name)
		
			// Specifying the source file allows the 'view code' 
			// button on the tool dialog to be displayed.
			def pathSep = File.separator
			def scriptFile = pluginHost.getResourcesDirectory() + "plugins" + pathSep + "Scripts" + pathSep + name + ".groovy"
			sd.setSourceFile(scriptFile)
			
			// add some components to the dialog
			sd.addDialogMultiFile("Select the input LAS files", "Input LAS Files:", "LAS Files (*.las), LAS")
			sd.addDialogDataInput("Output File Suffix (e.g. ground points)", "Output File Suffix (e.g. ground points)", "", false, true)
			sd.addDialogDataInput("Max Search Distance (m)", "Max Search Distance (m)", "", true, false)
			sd.addDialogDataInput("Threshold in the slope between points to define an off-terrain point.", "Inter-point Slope Threshold:", "30.0", true, false)
			
			// resize the dialog to the standard size and display it
			sd.setSize(800, 400)
			sd.visible = true
		}
	}

	// The CompileStatic annotation can be used to significantly
	// improve the performance of a Groovy script to nearly 
	// that of native Java code.
	@CompileStatic
	private void execute(String[] args) {
		long start = System.currentTimeMillis()
		int progress = 0
	    int oldProgress = -1
	    double x, y, z, zN, dist, slope, maxDist, maxSlope
	    double higherZ, lowerZ
		int a, i, p, higherPointIndex
	    double[] entry
	    PointRecord point;
	    List<KdTree.Entry<InterpolationRecord>> results
	    InterpolationRecord value
	    
	  	try {
		  	if (args.length != 4) {
				pluginHost.showFeedback("Incorrect number of arguments given to tool.")
				return
			}
			
			// read the input parameters
			final String inputFileString = args[0]
			String suffix = ""
			if (args[1] != null) {
				suffix = " " + args[1].trim();
			}
	        maxDist = Double.parseDouble(args[2]);
			maxSlope = Double.parseDouble(args[3]);
	        double slopeThreshold = Math.toRadians(maxSlope)
	        
			String[] inputFiles = inputFileString.split(";")
	
			// check for empty entries in the inputFiles array
			final int numFiles = inputFiles.length
			i = 0
	        for (String inputFile : inputFiles) {
				if (!inputFile.isEmpty()) {
					// create the LAS file reader
					LASReader las = new LASReader(inputFile);
		            int numPoints = (int) las.getNumPointRecords();
		            BooleanBitArray1D isOffTerrain = new BooleanBitArray1D(numPoints);
					
		            // Read the points into the k-dimensional tree.
		            KdTree<InterpolationRecord> pointsTree = new KdTree.SqrEuclid<InterpolationRecord>(2, new Integer(numPoints))
					for (a = 0; a < numPoints; a++) {
		                point = las.getPointRecord(a);
		                if (!point.isPointWithheld()) {
		                    x = point.getX();
		                    y = point.getY();
		                    z = point.getZ();
		                    
		                    entry = [x, y];
		                    pointsTree.addPoint(entry, new InterpolationRecord(z, a));
		                }
		                progress = (int) (100f * (a + 1) / numPoints);
		                if (progress != oldProgress) {
		                    oldProgress = progress;
		                    pluginHost.updateProgress("Reading point data:", progress);
		                    if (pluginHost.isRequestForOperationCancelSet()) {
		                        pluginHost.showFeedback("Operation cancelled")
								return
		                    }
		                }
		            }

		            /* visit the neighbourhood around each point hunting for 
		               points that are above other points with a slope greater 
		               than the threshold. */

		            for (a = 0; a < numPoints; a++) {
		            	if (!isOffTerrain.getValue(a)) {
							point = las.getPointRecord(a);
			                if (!point.isPointWithheld()) {
			                    x = point.getX();
			                    y = point.getY();
			                    z = point.getZ();
			                    
			                    entry = [x, y];
			                    results = pointsTree.neighborsWithinRange(entry, maxDist);

			                	for (p = 0; p < results.size() - 1; p++) {
			                		dist = Math.sqrt(results.get(p).distance);
			                		if (dist > 0) {
			                			value = (InterpolationRecord)results.get(p).value;
			                			zN = value.getZ()
			                			if (z > zN) {
			                				higherZ = z
			                				lowerZ = zN
			                				higherPointIndex = a
			                			} else {
			                				higherZ = zN
			                				lowerZ = z
			                				higherPointIndex = value.getIndex()
			                			}
			                			slope = Math.atan((higherZ - lowerZ) / dist)
										if (slope > slopeThreshold) {
											isOffTerrain.setValue(higherPointIndex, true)
										}
			                		}
			                	}
			                }
						}
						progress = (int) (100f * (a + 1) / numPoints);
		                if (progress != oldProgress) {
		                    oldProgress = progress;
		                    pluginHost.updateProgress("Finding ground points:", progress);
		                    if (pluginHost.isRequestForOperationCancelSet()) {
		                        pluginHost.showFeedback("Operation cancelled")
								return
		                    }
		                }
		            }

					/* 
					 *  Output the ground points to a shapefile.
					 */
					DBFField[] fields = new DBFField[2];
	
		            fields[0] = new DBFField();
		            fields[0].setName("Z");
		            fields[0].setDataType(DBFField.DBFDataType.NUMERIC);
		            fields[0].setFieldLength(10);
		            fields[0].setDecimalCount(3);
		
		            fields[1] = new DBFField();
		            fields[1].setName("I");
		            fields[1].setDataType(DBFField.DBFDataType.NUMERIC);
		            fields[1].setFieldLength(8);
		            fields[1].setDecimalCount(0);
		
					String outputFile = inputFile.replace(".las", suffix + ".shp");
	
			        // see if the output files already exist, and if so, delete them.
			        File outFile = new File(outputFile);
			        if (outFile.exists()) {
			            outFile.delete();
			            (new File(outputFile.replace(".shp", ".dbf"))).delete();
			            (new File(outputFile.replace(".shp", ".shx"))).delete();
			        }
		            
		            ShapeFile output = new ShapeFile(outputFile, ShapeType.POINT, fields);

					for (a = 0; a < numPoints; a++) {
		            	if (!isOffTerrain.getValue(a)) {
							point = las.getPointRecord(a);
			                if (!point.isPointWithheld()) {
			                    x = point.getX();
			                    y = point.getY();
			                    z = point.getZ();
			                    whitebox.geospatialfiles.shapefile.Point wbGeometry = new whitebox.geospatialfiles.shapefile.Point(x, y);

			                    Object[] rowData = new Object[2];
			                    rowData[0] = z;
			                    rowData[1] = (double) point.getIntensity();
			
			                    output.addRecord(wbGeometry, rowData);
			                }
						}
						progress = (int) (100f * (a + 1) / numPoints);
		                if (progress != oldProgress) {
		                    oldProgress = progress;
		                    pluginHost.updateProgress("Outputting point data:", progress);
		                    if (pluginHost.isRequestForOperationCancelSet()) {
		                        pluginHost.showFeedback("Operation cancelled")
								return
		                    }
		                }
		            }

		            output.write()
				
				// update progress bar for number of completed files
				i++
				progress = (int)(100f * i / numFiles)
				pluginHost.updateProgress("Progress", progress)
				// check to see if the user has requested a cancellation
				if (pluginHost.isRequestForOperationCancelSet()) {
					pluginHost.showFeedback("Operation cancelled")
					return
				}
				}
	        }

			pluginHost.showFeedback("Operation complete")
		
	  	} catch (OutOfMemoryError oe) {
            pluginHost.showFeedback("An out-of-memory error has occurred during operation.")
	    } catch (Exception e) {
	        pluginHost.showFeedback("An error has occurred during operation. See log file for details.")
	        pluginHost.logException("Error in " + descriptiveName, e)
	    } finally {
	    	// reset the progress bar
	    	pluginHost.updateProgress("Progress", 0)
	    }
	}
	
	@Override
    public void actionPerformed(ActionEvent event) {
    	if (event.getActionCommand().equals("ok")) {
    		final def args = sd.collectParameters()
			sd.dispose()
			final Runnable r = new Runnable() {
            	@Override
            	public void run() {
                	execute(args)
            	}
        	}
        	final Thread t = new Thread(r)
        	t.start()
    	}
    }

	class InterpolationRecord {
        
        double z;
        int index;
        
        InterpolationRecord(double z, int index) {
            this.z = z;
            this.index = index;
        }

        double getZ() {
            return z;
        }
        
        int getIndex() {
        	return index;
        }
    }
}

if (args == null) {
	pluginHost.showFeedback("Plugin arguments not set.")
} else {
	def myClass = new IsolateGroundPoints(pluginHost, args, name, descriptiveName)
}