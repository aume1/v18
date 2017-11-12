package org.metrobots.botcv.cv;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Path;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.metrobots.botcv.Log2File.Logger;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.core.*;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.lang.reflect.Array;
import java.security.spec.EllipticCurve;
import java.util.ArrayList;
import java.util.List;

/*
 * Interface class for the camera
 * Created by Tasgo on 1/16/16.
 * Last modified: 02/25/17
 */
public class CameraImpl implements CvCameraViewListener {

    //Initializing variables for future use

    private Mat frame = new Mat();
    private Mat hsv = new Mat();
    private Mat hierarchy = new Mat();
    private ArrayList<MatOfPoint> contours = new ArrayList<>();
    private Mat contourFrame = new Mat();
    private Point offset = new Point();
    private int xOffset = 0;
    private int yOffset = 0;
    private int status = 2;

    private int direction = 3;
    private int magnitude = 0;

    private Mat hsv2 = new Mat();
    private long oldMillis = 0;
    private int thresholSet = 0;
    private static final String MEASURE = "Rectangle measurement";
    private String measureInfoWidth = "Width";
    private String measureInfoHeight = "Height";

    //relevant logging variables
    private static double relativeDeltaX = 0.0;
    private static double relativeDeltaY = 0.0;
    private static int oldContourCount = 0;
    private static String oldCenterHSVString = "";
    private static int frameNumber = 0;

    private static final double PERFECT_X = 360; //temporary values
    private static final double PERFECT_Y = 220;

    private static final String DIRECTION = "Direction";
    private String seeDirection = "The direction";

    private static final String MAGNITUDE = "Magnitude";
    private String seeMagnitude = "The magnitude";


    private static String centerHSVString = "N/A";


    //temp code
    private LimiterSlider limiterSlider;

    public CameraImpl() {
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(Mat inputFrame) {

        return cameraFrame(inputFrame);
    }


    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        return cameraFrame(inputFrame.rgba());
    }


    public Mat cameraFrame(Mat mat) {

        //Empty's frames and lists to ensure the app does not crash and reduces lag
        frame.empty();
        hsv.empty();
        hsv2.empty();
        hierarchy.empty();
        contours.clear();

        //Converts the RGB frame to the HSV frame

        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);

        if (Math.abs(System.currentTimeMillis() - oldMillis) > 1000 && thresholSet < 5) {
            oldMillis = System.currentTimeMillis();
            thresholSet++;
        } else if (Math.abs(System.currentTimeMillis() - oldMillis) > 1000) {
            oldMillis = System.currentTimeMillis();
            thresholSet = 0;
        }

        //creates a copy so the original is unaffected
        hsv.copyTo(hsv2);

        //tries to remove random splotches of contours

        //Imgproc.bilateralFilter(hsv, hsv2, 3, 10, 10);
        Imgproc.blur(hsv, hsv, new Size(10,10));
        //Imgproc.blur(hsv, hsv, new Size(10,10));

        /*int goodH = 25;
        int goodS = 17;
        int goodV = 100;*/ //This was good for the vision strips.

        int goodH = 70; // These are good for yellow stress ball
        int goodS = 100;
        int goodV = 70;

        int thresholdH = 20;  //was 40
        int thresholdS = 50; //was 40
        int thresholdV = 70; //was 30

        goodH = goodH * 255 / 360;
        goodS = goodS * 255 / 100;
        goodV = goodV * 255 / 100;


        int lowH = goodH - thresholdH;
        int lowS = goodS - thresholdS;
        int lowV = goodV - thresholdV;

        int highH = goodH + thresholdH;
        int highS = goodS + thresholdS;
        int highV = goodV + thresholdV;

        //filters out colors outside of the set range of hsv //*100/255
        // Good color is 150, 180, 20
        //Core.inRange(hsv, new Scalar(45, 100, 150), new Scalar(70, 255, 255), frame);
        //Core.inRange(hsv, new Scalar(0, 0, 175), new Scalar(255, 100, 255), frame);
        //Core.inRange(hsv, new Scalar(0, 0, 175), new Scalar(255, 100, 255), frame);
        Core.inRange(hsv, new Scalar(lowH, lowS, lowV), new Scalar(highH, highS, highV), frame);


        //Copies the black and white image to a new frame to prevent messing up the original
        frame.copyTo(contourFrame);

        //Point center1 = new Point(PERFECT_X, PERFECT_Y);

        //Log.i("Center color = ", frame.get((int)center1.x, (int)center1.y)[0] + frame.get((int)center1.x, (int)center1.y)[1] + frame.get((int)center1.x, (int)center1.y)[2] + "");
        int centery = hsv.width() / 2;
        int centerx = hsv.height() / 2;
        double[] hsvvalue = hsv.get(centerx, centery);

        hsvvalue[0] = hsvvalue[0] * 360 / 255;
        hsvvalue[1] = hsvvalue[1] * 100 / 255;
        hsvvalue[2] = hsvvalue[2] * 100 / 255;

        /*Log.i("H", "" + hsvvalue[0]);
        Log.i("S", "" + hsvvalue[1]);
        Log.i("V", "" + hsvvalue[2]);*/

        //Logger.log("HSV", "H: " + (int)hsvvalue[0] + " S: " + (int)hsvvalue[1] + " V: " + (int)hsvvalue[2]);

        //Finds the contours in the thresholded frame
        Imgproc.findContours(contourFrame, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        //Draws the contours found on the original camera feed
        Imgproc.drawContours(mat, contours, -2,
                new Scalar(0, 0, 255), 5, 8, hierarchy, Imgproc.INTER_MAX, offset);
        //Draws circle at the center of the feed
        Imgproc.circle(mat, new Point((mat.size().width) / 2, (mat.size().height) / 2),
                5, new Scalar(255, 255, 0), 15, Imgproc.LINE_8, 0);


        ArrayList<MatOfPoint> newContours = (ArrayList)contours.clone();
        newContours.clear();
        int newContoursIndex = 0;

        /*for (int i = 0; i < contours.size(); i++) {
            RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            if ((rect.size.area() - Imgproc.contourArea(contours.get(i))) < 1500 && rect.size.area() > 500) {
                Point points[] = new Point[4];
                rect.points(points);
                for(int q=0; q<4; ++q){
                    Imgproc.line(mat, points[q], points[(q+1)%4], new Scalar(255,0,255), 3);
                }
                newContours.add(newContoursIndex,contours.get(i));
                newContoursIndex++;
            }
        }*/

        for (int i = 0; i < contours.size(); i++) {
            RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            Point points[] = new Point[4];
            rect.points(points);
            Point lowestY = new Point(0,0);
            Point lowestY2 = new Point(0,0);
            Point highestY = new Point(800,800);
            Point highestY2 = new Point(800,800);
            for(int q=0; q<4; ++q){
                Imgproc.line(mat, points[q], points[(q+1)%4], new Scalar(255,255,255), 3);
                if (points[q].y > lowestY.y) {
                    lowestY2 = lowestY;
                    lowestY = points[q];
                    //Imgproc.circle(mat, lowestX, 5, new Scalar(255, 255, 255), 30, Imgproc.LINE_8, 0);
                } else if (points[q].y > lowestY2.y && points[q].y <= lowestY.y) {
                    lowestY2 = points[q];
                }

                if (points[q].y < highestY.y) {
                    highestY2 = highestY;
                    highestY = points[q];
                } else if (points[q].y < highestY2.y && points[q].y >= highestY.y) {
                    highestY2 = points[q];
                }

            }
            double midx = 0;
            double midy = 0;
            for (Point p : points) {
                midx += p.x;
                midy += p.y;
            }
            midx = midx/4;
            midy = midy/4;
            if (Imgproc.contourArea(contours.get(i)) > 500) {
                List<Moments> mu = new ArrayList<Moments>(1);
                mu.add(0, Imgproc.moments(contours.get(i), false));
                Moments p = mu.get(0);
                int x = (int) (p.get_m10() / p.get_m00());
                int y = (int) (p.get_m01() / p.get_m00());
                //Core.circle(mat, new Point(x, y), 4, new Scalar(255,49,0,255));
                Imgproc.circle(mat, new Point(x, y), 5, new Scalar(255, 255, 255), 30, Imgproc.LINE_8, 0);


                int topY = (int)(highestY.y + highestY2.y) / 2;
                int yHeight = 2 * (y - topY);
                Point bottomY = new Point(x, topY + yHeight);
                Imgproc.circle(mat, bottomY, 5, new Scalar(255, 255, 255), 30, Imgproc.LINE_8, 0);

                Mat lines = new Mat();
                int threshold = 5;
                int minLineSize = 20;
                int lineGap = 2;

                MatOfPoint2f approx = new MatOfPoint2f();
                MatOfPoint2f curve = new MatOfPoint2f( contours.get(i).toArray() );
                MatOfPoint approxf1 = new MatOfPoint();
                Imgproc.approxPolyDP(curve, approx, 0.02, true);
                // All these lines are to print the contour (optional)
                approx.convertTo(approxf1, CvType.CV_32S);
                List<MatOfPoint> contourTemp = new ArrayList<>();
                contourTemp.add(approxf1);
                Imgproc.drawContours(mat, contourTemp, 0, new Scalar(0, 255, 0), 2);

                // EMail to share github repo with: cuiyy991@gmail.com

                /*Imgproc.HoughLinesP(contourFrame, lines, 1, Math.PI/180, threshold, minLineSize, lineGap);



                for (int z = 0; z < lines.cols(); z++) {
                    double[] vec = lines.get(0, z);
                    double x1 = vec[0],
                            y1 = vec[1],
                            x2 = vec[2],
                            y2 = vec[3];
                    Point start = new Point(x1, y1);
                    Point end = new Point(x2, y2);

                    Imgproc.line(mat, start, end, new Scalar(255,0,0), 3);

                }*/
            }
            //Imgproc.circle(mat, lowestY2, 5, new Scalar(255, 255, 255), 30, Imgproc.LINE_8, 0);
        }

        /*for (int i = 0; i < contours.size(); i++) {
            //Imgproc.fitEllipse(new MatOfPoint2f(contours.get(i)));
            if (contours.get(i).size().area() > 100) {
                MatOfPoint2f temp = new MatOfPoint2f();
                temp.fromList(contours.get(i).toList());
                RotatedRect rect = Imgproc.fitEllipse(temp);
                int rad = (int)(rect.size.height + rect.size.width)/4;
                Imgproc.circle(mat, rect.center, rad, new Scalar(0, 255, 0), -1);
                Imgproc.circle(mat, rect.center, rad/2, new Scalar(0, 0, 0), rad/4);
                Imgproc.circle(mat, rect.center, rad/2, new Scalar(255, 255, 255), 5);
                Imgproc.ellipse(mat, rect, new Scalar(0,0,0), 5);
            }
                //Imgproc.ellipse(mat, Imgproc.fitEllipse(new MatOfPoint2f(contours.get(i))), new Scalar(0,0,0));
            /*newContours.add(newContoursIndex,contours.get(i));
            newContoursIndex++;
        }*/
        contours = newContours;




        try {
            //Creates the max variable
            int max = 0;
            int max2 = 0;
            double maxArea = 0;
            double maxArea2 = 0;
            int contourNumber = 0;

            double contourAreaMin = 0.0;
            //Sets up loop to go through all contours
            for (int a = 0; a < contours.size(); a++) {
                //Gets the area of all of the contours
                double s2 = Imgproc.contourArea(contours.get(a));
                //Log.i("Contour area ", Double.toString(s2));
                //Doesn't look at contours lower than 900
                if (s2 > contourAreaMin && s2 > 900) {
                    //Log.i("S2 > contourAreaMin ", "It worked!");
                    //continue;
                    //Checks the area against the other areas of the contours to find out which is largest
                    if (s2 > maxArea) {
                        //Sets largest contour equal to max variable
                        max = a;
                        maxArea = Imgproc.contourArea(contours.get(max));
                        //Log.i("Maximum area ", Double.toString(s2));
                    } else if (s2 > maxArea2) {
                        max2 = a;
                        maxArea2 = Imgproc.contourArea(contours.get(max2));
                    }
                    if (s2 > contourAreaMin) {
                        contourNumber += 1;
                        //Log.i("Contour number ", Integer.toString(contourNumber));
                    }
                }
            }

            //System.out.println("");
            try {
                //System.out.println(Imgproc.contourArea(contours.get(max)));
                //Gets the minimum area vertical(non titlted) rectangle that outlines the contour
                Rect place = Imgproc.boundingRect(contours.get(max));
                Rect place2 = Imgproc.boundingRect(contours.get(max2));


                //System.out.println("Top Left Coordinate: " + place.tl());

                //Creates variable for center point
                Point center = new Point();

                //Sets variable fpr screen center so now we adjust the X and Y axis
                //Point screenCenter = new Point();
                //Creates top left point variable
                Point topleft = place.tl();
                Point topleft2 = place2.tl();

                //Creates bottom right point variable
                Point bottomright = place.br();
                Point bottomright2 = place2.br();


                //Finds the width of rectangle
                double width = (bottomright.x - topleft.x);
                double height = (bottomright.y - topleft.y); //not used because no magnitude

                //double xcenter = topleft.x + width/2;
                //double ycenter = topleft.y + height/2;

                //center.x = topleft.x + width/2;
                //center.y = topleft.y + height/2;

                double contourCenterx = hsv.width()/2;
                double contourCentery = hsv.height()/2;

                double[] centerHSV = hsv.get((int)contourCenterx, (int)contourCentery);
                centerHSV = hsv.get(frame.width(), frame.height());

                centerHSV[0] = centerHSV[0] * 360 / 255;
                centerHSV[1] = centerHSV[1] * 100 / 255;
                centerHSV[2] = centerHSV[2] * 100 / 255;

                centerHSVString = "H: " + (int)centerHSV[0] + " S: " + (int)centerHSV[1] + " V: " + (int)centerHSV[2];
                //logging segment
                if (frameNumber == 0 ) {
                    if (centerHSVString != oldCenterHSVString) {
                        Logger.log("Main Contour HSV", centerHSVString);
                    }
                    frameNumber = 1;
                } else {
                    if (frameNumber >= 4) {
                        frameNumber = 0;
                    } else {
                        frameNumber += 1;
                    }
                }
                oldCenterHSVString = centerHSVString;

                if (contourNumber != oldContourCount) {
                    Logger.log("cCount", "value: " + contourNumber);
                    oldContourCount = contourNumber;
                }


                relativeDeltaX = (PERFECT_X - center.x);
                relativeDeltaY = (PERFECT_Y - center.y); //print out message in logcat so there is no error if no contour found

                xOffset = (int) relativeDeltaX;
                yOffset = (int) relativeDeltaY;

                //Finding the middle of the countoured area on the screen
                center.x = (topleft.x + bottomright.x) / 2;
                center.y = (topleft.y + bottomright.y) / 2;



                //Draws the circle at center of contoured object
                Imgproc.circle(mat, center, 5, new Scalar(255, 0, 255),
                        5, Imgproc.LINE_8, 0);
                //Draws rectangle around the recognized contour
                //Draws the circle at center of contoured object

                //Draws rectangle around the recognized contour
                Imgproc.rectangle(mat, place.tl(), place.br(),
                        new Scalar(255, 0, 0), 10, Imgproc.LINE_8, 0);

            } catch (Exception e) {
                //This is
                //status = 2;
                //Log.i("error1: ", e.toString());
            }
            return mat; //frame
        } catch (Exception e) {
            //In case no contours are found, returns the error status
            status = 2;
            //Log.i("error2: ", e.toString());
        }
        //Returns the original image with drawn contours and shape identifiers
        return mat; //was mat
    }



    public int getStatus() {
        return status;
    }

    public int getMagnitude() {
        return magnitude;
    }

    public int getDirection() {
        return direction;
    }

    public String getHSV() { return centerHSVString; }

    public int getXOffset() { return xOffset; }

    public int getYOffset() { return yOffset; }

}

