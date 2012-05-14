package plugins;

import java.util.Date;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.interfaces.WhiteboxPluginHost;
import whitebox.interfaces.WhiteboxPlugin;

/**
 *
 * @author johnlindsay
 */
public class EuclideanAllocation implements WhiteboxPlugin {
    
    private WhiteboxPluginHost myHost = null;
    private String[] args;

    @Override
    public String getName() {
        return "EuclideanAllocation";
    }

    @Override
    public String getDescriptiveName() {
    	return "Euclidean Allocation";
    }

    @Override
    public String getToolDescription() {
    	return "Assigns grid cells in the output image the value of the nearest "
                + "target cell in the input image, measured by the Euclidean distance.";
    }

    @Override
    public String[] getToolbox() {
    	String[] ret = { "DistanceTools" };
    	return ret;
    }

    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }

    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }

    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }

    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
    }
    
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    @Override
    public boolean isActive() {
        return amIActive;
    }

    @Override
    public void run() {
        amIActive = true;
        
        String inputHeader = null;
        String outputHeader = null;
        int row, col;
        float progress = 0;
        double z, z2, zMin;
        int x, y, a, b, i;
        double h = 0;
        int whichCell;
        double infVal = 9999999; //Float.MAX_VALUE - 10000;
        int[] dX = new int[]{-1, -1, 0, 1, 1, 1, 0, -1};
        int[] dY = new int[]{0, -1, -1, -1, 0, 1, 1, 1};
        int[] Gx = new int[]{1, 1, 0, 1, 1, 1, 0, 1};
        int[] Gy = new int[]{0, 1, 1, 1, 0, 1, 1, 1};
        
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        for (i = 0; i < args.length; i++) {
            if (i == 0) {
                inputHeader = args[i];
            } else if (i == 1) {
                outputHeader = args[i];
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((inputHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            WhiteboxRaster image = new WhiteboxRaster(inputHeader, "r");
            
            int rows = image.getNumberRows();
            int cols = image.getNumberColumns();
            double noData = image.getNoDataValue();
            
            WhiteboxRaster allocation = new WhiteboxRaster(outputHeader, "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, infVal);
            allocation.setPreferredPalette(image.getPreferredPalette());
            
            WhiteboxRaster outputImage = new WhiteboxRaster(outputHeader.replace(".dep", "_temp1.dep"), "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, infVal);
            outputImage.isTemporaryFile = true;
            WhiteboxRaster Rx = new WhiteboxRaster(outputHeader.replace(".dep", "_temp2.dep"), "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, 0);
            Rx.isTemporaryFile = true;
            WhiteboxRaster Ry = new WhiteboxRaster(outputHeader.replace(".dep", "_temp3.dep"), "rw", inputHeader, WhiteboxRaster.DataType.FLOAT, 0);
            Ry.isTemporaryFile = true;
            
            double[] data;
            for (row = 0; row < rows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < cols; col++) {
                    if (data[col] !=0) { 
                        outputImage.setValue(row, col, 0);
                        allocation.setValue(row, col, data[col]);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (rows - 1));
                updateProgress((int) progress);
            }

            for (row = 0; row < rows; row++) {
                for (col = 0; col < cols; col++) {
                    z = outputImage.getValue(row, col);
                    if (z != 0) {
                        zMin = infVal;
                        whichCell = -1;
                        for (i = 0; i <= 3; i++) {
                            x = col + dX[i];
                            y = row + dY[i];
                            z2 = outputImage.getValue(y, x);
                            if (z2 != noData) {
                                switch (i) {
                                    case 0:
                                        h = 2 * Rx.getValue(y, x) + 1;
                                        break;
                                    case 1:
                                        h = 2 * (Rx.getValue(y, x) + Ry.getValue(y, x) + 1);
                                        break;
                                    case 2:
                                        h = 2 * Ry.getValue(y, x) + 1;
                                        break;
                                    case 3:
                                        h = 2 * (Rx.getValue(y, x) + Ry.getValue(y, x) + 1);
                                        break;
                                }
                                z2 += h;
                                if (z2 < zMin) {
                                    zMin = z2;
                                    whichCell = i;
                                }
                            }
                        }
                        if (zMin < z) {
                            outputImage.setValue(row, col, zMin);
                            x = col + dX[whichCell];
                            y = row + dY[whichCell];
                            Rx.setValue(row, col, Rx.getValue(y, x) + Gx[whichCell]);
                            Ry.setValue(row, col, Ry.getValue(y, x) + Gy[whichCell]);
                            allocation.setValue(row, col, allocation.getValue(y, x));
                        }
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (rows - 1));
                updateProgress((int) progress);
            }

            
            for (row = rows - 1; row >= 0; row--) {
                for (col = cols - 1; col >= 0; col--) {
                    z = outputImage.getValue(row, col);
                    if (z != 0) {
                        zMin = infVal;
                        whichCell = -1;
                        for (i = 4; i <= 7; i++) {
                            x = col + dX[i];
                            y = row + dY[i];
                            z2 = outputImage.getValue(y, x);
                            if (z2 != noData) {
                                switch (i) {
                                    case 5:
                                        h = 2 * (Rx.getValue(y, x) + Ry.getValue(y, x) + 1);
                                        break;
                                    case 4:
                                        h = 2 * Rx.getValue(y, x) + 1;
                                        break;
                                    case 6:
                                        h = 2 * Ry.getValue(y, x) + 1;
                                        break;
                                    case 7:
                                        h = 2 * (Rx.getValue(y, x) + Ry.getValue(y, x) + 1);
                                        break;
                                }
                                z2 += h;
                                if (z2 < zMin) {
                                    zMin = z2;
                                    whichCell = i;
                                }
                            }
                        }
                        if (zMin < z) {
                            outputImage.setValue(row, col, zMin);
                            x = col + dX[whichCell];
                            y = row + dY[whichCell];
                            Rx.setValue(row, col, Rx.getValue(y, x) + Gx[whichCell]);
                            Ry.setValue(row, col, Ry.getValue(y, x) + Gy[whichCell]);
                            allocation.setValue(row, col, allocation.getValue(y, x));
                        }
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * (rows - 1 - row) / (rows - 1));
                updateProgress((int) progress);
            }
            
            for (row = 0; row < rows; row++) {
                data = image.getRowValues(row);
                for (col = 0; col < cols; col++) {
                    if (data[col] == noData) {
                        allocation.setValue(row, col, noData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (rows - 1));
                updateProgress((int) progress);
            }
            
            allocation.addMetadataEntry("Created by the "
                    + getDescriptiveName() + " tool.");
            allocation.addMetadataEntry("Created on " + new Date());
            
            image.close();
            allocation.close();
            outputImage.close();
            Rx.close();
            Ry.close();

            // returning a header file string displays the image.
            returnData(outputHeader);

        } catch (Exception e) {
            showFeedback(e.getMessage());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
}