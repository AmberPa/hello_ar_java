/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.helloar.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.helloar.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private GestureDetector gestureDetector;
  private Snackbar messageSnackbar;
  private DisplayRotationHelper displayRotationHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final ObjectRenderer virtualObject = new ObjectRenderer();
  private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloud = new PointCloudRenderer();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];

  // Tap handling and UI.
  private final ArrayBlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);
  private final ArrayList<Anchor> anchors = new ArrayList<>();


  //TODO TEST VARIABLES
  // FROM https://stackoverflow.com/questions/49141613/arcore-opengl-es-moving-object-using-onscroll

  ScaleGestureDetector mScaleDetector;
  private final List<Float> mScaleFactors = new ArrayList<Float>();
  private RotationGestureDetector mRotationDetector;
  private final List<Float> mRotationThetas = new ArrayList<Float>();
  private final List<Float> mTranslationX = new ArrayList<Float>();
  private final List<Float> mTranslationZ = new ArrayList<Float>();
  private final float[] mOriginCameraMatrix = new float[16];
  private final float[] mCurrentCameraMatrix = new float[16];
  private final List<ObjectRenderer> mVirtualObjects = new ArrayList<ObjectRenderer>();

  private final List<Integer> mAnchorReferences = new ArrayList<Integer>();

  private int mCurrent = -1;


  //TODO TEST VARIABLES END


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);


    //TODO TEST START


    //Handle Gestures - Multitouch for Scaling and Rotation
    mScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      @Override
      public boolean onScale(ScaleGestureDetector detector) {
        if (mScaleFactors.size() > 0) {

          mScaleFactors.set(mScaleFactors.size() - 1, Math.max(0.1f, Math.min(detector.getScaleFactor() * mScaleFactors.get(mScaleFactors.size() - 1), 5.0f)));

          return true;
        }
        return false;
      }
    });

    mRotationDetector = new RotationGestureDetector(this, new RotationGestureDetector.OnRotationGestureListener() {
      @Override
      public void OnRotation(RotationGestureDetector rotationDetector) {
        if (mRotationThetas.size() > 0) {
          mRotationThetas.set(mRotationThetas.size() - 1, (mRotationThetas.get(mRotationThetas.size() - 1) + (rotationDetector.getAngle() * -0.001f)));
        }
      }
    });


    //TODO TEST END


    // Set up tap listener.
    gestureDetector =
            new GestureDetector(
                    this,
                    new GestureDetector.SimpleOnGestureListener() {
                      @Override
                      public boolean onSingleTapUp(MotionEvent e) {
                        onSingleTap(e);
                        return true;
                      }


                      //TODO TEST
                      @Override
                      public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                        if (e2.getPointerCount() == 1 && mTranslationX.size() > 0 && mTranslationZ.size() > 0) {

                          double angle = findCameraAngleFromOrigin();
                          double speed = 0.001d;

                          if (angle / 90d < 1) //Quadrant 1
                          {
                            double transX = -(distanceY * (angle / 90d)) + (distanceX * ((90d - angle) / 90d));
                            double transY = (distanceY * ((90d - angle) / 90d)) + (distanceX * (angle / 90d));
//                        showSnackbarMessage("ANGLE: " + angle + ", distanceX: " + distanceX + ", distanceY: " + distanceY, false);
                            mTranslationX.set(mTranslationX.size() - 1, (float) (mTranslationX.get(mTranslationX.size() - 1) + (transX * -speed)));
                            mTranslationZ.set(mTranslationZ.size() - 1, (float) (mTranslationZ.get(mTranslationZ.size() - 1) + (transY * -speed)));
                          } else if (angle / 90d < 2) //Quadrant 2
                          {
                            angle -= 90d;
                            double transX = (distanceX * (angle / 90d)) + (distanceY * ((90d - angle) / 90d));
                            double transY = (-distanceX * ((90d - angle) / 90d)) + (distanceY * (angle / 90d));
//                        showSnackbarMessage("ANGLE: " + angle + ", distanceX: " + distanceX + ", distanceY: " + distanceY, false);
                            mTranslationX.set(mTranslationX.size() - 1, (float) (mTranslationX.get(mTranslationX.size() - 1) + (transX * speed)));
                            mTranslationZ.set(mTranslationZ.size() - 1, (float) (mTranslationZ.get(mTranslationZ.size() - 1) + (transY * speed)));
                          } else if (angle / 90d < 3) //Quadrant 3
                          {
                            angle -= 180d;
                            double transX = (distanceY * (angle / 90d)) + (-distanceX * ((90d - angle) / 90d));
                            double transY = (-distanceY * ((90d - angle) / 90d)) + (-distanceX * (angle / 90d));
//                        showSnackbarMessage("ANGLE: " + angle + ", distanceX: " + distanceX + ", distanceY: " + distanceY, false);
                            mTranslationX.set(mTranslationX.size() - 1, (float) (mTranslationX.get(mTranslationX.size() - 1) + (transX * -speed)));
                            mTranslationZ.set(mTranslationZ.size() - 1, (float) (mTranslationZ.get(mTranslationZ.size() - 1) + (transY * -speed)));
                          } else  //Quadrant 4
                          {
                            angle -= 270d;
                            double transX = (-distanceX * (angle / 90d)) + (-distanceY * ((90d - angle) / 90d));
                            double transY = (distanceX * ((90d - angle) / 90d)) + (-distanceY * (angle / 90d));
//                        showSnackbarMessage("ANGLE: " + angle + ", distanceX: " + distanceX + ", distanceY: " + distanceY, false);
                            mTranslationX.set(mTranslationX.size() - 1, (float) (mTranslationX.get(mTranslationX.size() - 1) + (transX * speed)));
                            mTranslationZ.set(mTranslationZ.size() - 1, (float) (mTranslationZ.get(mTranslationZ.size() - 1) + (transY * speed)));
                          }
                          return true;
                        }

                        return false;
                      }

                      //TODO TEST END


                      @Override
                      public boolean onDown(MotionEvent e) {
                        return true;
                      }
                    });

    surfaceView.setOnTouchListener(
            new View.OnTouchListener() {
              @Override
              public boolean onTouch(View v, MotionEvent event) {
                //TODO TEST
                boolean retVal = mScaleDetector.onTouchEvent(event);
                if (retVal)
                  mRotationDetector.onTouchEvent(event);
                retVal = gestureDetector.onTouchEvent(event) || retVal;
                return retVal || gestureDetector.onTouchEvent(event);
                //TODO TEST END
                //return gestureDetector.onTouchEvent(event);
              }
            });


    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    installRequested = false;
  }


  //TODO MORE TEST
  private double findCameraAngleFromOrigin() {
    double angle = getDegree(mCurrentCameraMatrix[2], mCurrentCameraMatrix[0]) -
            getDegree(mOriginCameraMatrix[2], mOriginCameraMatrix[0]);
    if (angle < 0)
      return angle + 360;
    return angle;
  }

  private double getDegree(double value1, double value2) {
    double firstAngle = value1 * 90;
    double secondAngle = value2 * 90;
    if (secondAngle >= 0 && firstAngle >= 0) {
      return firstAngle; // first quadrant
    } else if (secondAngle < 0 && firstAngle >= 0) {
      return 90 + (90 - firstAngle); //second quadrant
    } else if (secondAngle < 0 && firstAngle < 0) {
      return 180 - firstAngle; //third quadrant
    } else {
      return 270 + (90 + firstAngle); //fourth quadrant
    }
  }
//TODO TEST END


  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
              | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (Exception e) {
        message = "This device does not support AR";
        exception = e;
      }

      if (message != null) {
        showSnackbarMessage(message, true);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      // Create default config and check if supported.
      Config config = new Config(session);
      if (!session.isSupported(config)) {
        showSnackbarMessage("This device does not support AR", true);
      }
      session.configure(config);
    }

    showLoadingMessage();
    // Note that order matters - see the note in onPause(), the reverse applies here.
    session.resume();
    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
              .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      // Standard Android full-screen functionality.
      getWindow()
              .getDecorView()
              .setSystemUiVisibility(
                      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                              | View.SYSTEM_UI_FLAG_FULLSCREEN
                              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  private void onSingleTap(MotionEvent e) {
    // Queue tap if there is space. Tap is lost if queue is full.
    queuedSingleTaps.offer(e);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Create the texture and pass it to ARCore session to be filled during update().
    backgroundRenderer.createOnGlThread(/*context=*/ this);

    // Prepare the other rendering objects.
    try {
      virtualObject.createOnGlThread(/*context=*/ this, "andy.obj", "andy.png");
      virtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);

      virtualObjectShadow.createOnGlThread(/*context=*/ this, "andy_shadow.obj", "andy_shadow.png");
      virtualObjectShadow.setBlendMode(BlendMode.Shadow);
      virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read obj file");
    }
    try {
      planeRenderer.createOnGlThread(/*context=*/ this, "trigrid.png");
    } catch (IOException e) {
      Log.e(TAG, "Failed to read plane texture");
    }
    pointCloud.createOnGlThread(/*context=*/ this);
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
    mVirtualObjects.add(virtualObject);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Handle taps. Handling only one tap per frame, as taps are usually low frequency
      // compared to frame rate.

      MotionEvent tap = queuedSingleTaps.poll();
      if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
        for (HitResult hit : frame.hitTest(tap)) {
          // Check if any plane was hit, and if it was hit inside the plane polygon
          Trackable trackable = hit.getTrackable();
          // Creates an anchor if a plane or an oriented point was hit.
          if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                  || (trackable instanceof Point
                  && ((Point) trackable).getOrientationMode()
                  == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
            // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
            // Cap the number of objects created. This avoids overloading both the
            // rendering system and ARCore.
            if (anchors.size() >= 20) {
              //anchors.get(0).detach();
              //anchors.remove(0);

              //TODO Test

              //Alert the user that the maximum has been reached, must call from
              //Handler as this is a UI action being done on a worker thread.
              new Handler(Looper.getMainLooper())
              {
                @Override
                public void handleMessage(Message message)
                {
                  Toast.makeText(HelloArActivity.this,
                          "You've reached the maximum!", Toast.LENGTH_LONG).show();
                }
              };


            } else {
              // Adding an Anchor tells ARCore that it should track this position in
              // space. This anchor is created on the Plane to place the 3d model
              // in the correct position relative both to the world and to the plane.
              mScaleFactors.add(1.0f);
              Anchor anchor = hit.createAnchor();
              anchors.add(anchor);
              mAnchorReferences.add(mCurrent);
              camera.getDisplayOrientedPose().toMatrix(mOriginCameraMatrix, 0);

            }

            //TODO TEST END


            //TODO MY BIT
            Pose anchorPose;
            Pose hitPose;

            hitPose = hit.getHitPose();


            //maybe destroy an anchor on touch
            for (Anchor anchor : anchors){
              anchorPose = anchor.getPose();

              if (anchorPose == hitPose){
                anchor.detach();
                anchors.remove(anchor);
              }
            }

            Boolean inRange = false;

            for (Anchor anchor : anchors){
              anchorPose = anchor.getPose();

              inRange = NEWisWithinRange(hitPose, anchorPose);
              mCurrent = anchors.indexOf(anchor);
              //if the android is in range
              if (inRange){

                //to explode the andys on touch:
                //anchor.detach();
                //anchors.remove(anchor);
              }
            }

            //TODO MY BIT END


            //if (!inRange) {
            //anchors.add(hit.createAnchor());
            //}
            // Adding an Anchor tells ARCore that it should track this position in
            // space. This anchor is created on the Plane to place the 3D model
            // in the correct position relative both to the world and to the plane.
            break;
          }
        }
      }

      // Draw background.
      backgroundRenderer.draw(frame);

      // If not tracking, don't draw 3d objects.
      if (camera.getTrackingState() == TrackingState.PAUSED) {
        return;
      }

      // Get projection matrix.
      float[] projmtx = new float[16];
      camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewmtx = new float[16];
      camera.getViewMatrix(viewmtx, 0);

      //TODO User added:
      LightEstimate.State validity = frame.getLightEstimate().getState();
      float lightIntensity = frame.getLightEstimate().getPixelIntensity();

      while (validity == LightEstimate.State.NOT_VALID) {
        validity = frame.getLightEstimate().getState();
        // Compute lighting from average intensity of the image.
        lightIntensity = frame.getLightEstimate().getPixelIntensity();
      }


      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      this.pointCloud.update(pointCloud);
      this.pointCloud.draw(viewmtx, projmtx);

      // Application is responsible for releasing the point cloud resources after
      // using it.
      pointCloud.release();

      // Check if we detected at least one plane. If so, hide the loading message.
      if (messageSnackbar != null) {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
          if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
                  && plane.getTrackingState() == TrackingState.TRACKING) {
            hideLoadingMessage();
            break;
          }
        }
      }


      //TODO TEST
      camera.getDisplayOrientedPose().toMatrix(mCurrentCameraMatrix, 0);
      //TODO TEST END

      // Visualize planes.
      planeRenderer.drawPlanes(
              session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

      // Visualize anchors created by touch.
//      float scaleFactor = 1.0f;
//      for (Anchor anchor : anchors) {
//        if (anchor.getTrackingState() != TrackingState.TRACKING) {
//          continue;
//        }
//        // Get the current pose of an Anchor in world space. The Anchor pose is updated
//        // during calls to session.update() as ARCore refines its estimate of the world.
//        anchor.getPose().toMatrix(anchorMatrix, 0);
//
//        // Update and draw the model and its shadow.
//        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
//        virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
//        virtualObject.draw(viewmtx, projmtx, lightIntensity);
//        virtualObjectShadow.draw(viewmtx, projmtx, lightIntensity);
//      }


//TODO TEST
      int ac = 0;

      for (Anchor anchor : anchors) {
          if (anchor.getTrackingState() != TrackingState.TRACKING) {
            continue;
          }
          // Get the current pose of an Anchor in world space. The Anchor pose is updated
          // during calls to session.update() as ARCore refines its estimate of the world.
          anchor.getPose().toMatrix(anchorMatrix, 0);

          if (mScaleFactors.size() <= ac) {
            mScaleFactors.add(1.0f);
          }
          if (mRotationThetas.size() <= ac) {
            mRotationThetas.add(0.0f);
          }
          if (mTranslationX.size() <= ac) {
            mTranslationX.add(viewmtx[3]);
          }
          if (mTranslationZ.size() <= ac) {
            mTranslationZ.add(viewmtx[11]);
          }

          translateMatrix(mTranslationX.get(ac), 0, mTranslationZ.get(ac));
          rotateYAxisMatrix(mRotationThetas.get(ac));

          // Update and draw the model and its shadow.
          ObjectRenderer vitualObject = mVirtualObjects.get(mAnchorReferences.get(ac));

          vitualObject.updateModelMatrix(anchorMatrix, mScaleFactors.get(ac));
          vitualObject.draw(viewmtx, projmtx, lightIntensity);

          virtualObjectShadow.updateModelMatrix(anchorMatrix, mScaleFactors.get(ac));
          virtualObjectShadow.draw(viewmtx, projmtx, lightIntensity);
          //TODO TEST END


      }
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }

  private void showSnackbarMessage(String message, boolean finishOnDismiss) {
    messageSnackbar =
            Snackbar.make(
                    HelloArActivity.this.findViewById(android.R.id.content),
                    message,
                    Snackbar.LENGTH_INDEFINITE);
    messageSnackbar.getView().setBackgroundColor(0xbf323232);
    if (finishOnDismiss) {
      messageSnackbar.setAction(
              "Dismiss",
              new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                  messageSnackbar.dismiss();
                }
              });
      messageSnackbar.addCallback(
              new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                  super.onDismissed(transientBottomBar, event);
                  finish();
                }
              });
    }
    messageSnackbar.show();
  }

  private void showLoadingMessage() {
    runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                showSnackbarMessage("Searching for surfaces...", false);
              }
            });
  }

  private void hideLoadingMessage() {
    runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                if (messageSnackbar != null) {
                  messageSnackbar.dismiss();
                }
                messageSnackbar = null;
              }
            });
  }

  private boolean NEWisWithinRange(Pose hitPose, Pose anchorPose) {
    boolean withinRange = false;

    boolean XInRange = false;
    boolean YInRange = false;
    boolean ZInRange = false;

    float hitTX = hitPose.tx();
    float hitTY = hitPose.ty();
    float hitTZ = hitPose.tz();


    float anchorTX = anchorPose.tx();
    float anchorTY = anchorPose.ty();
    float anchorTZ = anchorPose.tz();

    float upperXBound;
    float upperYBound;
    float upperZBound;

    float lowerXBound;
    float lowerYBound;
    float lowerZBound;

    upperXBound = anchorTX + 0.1f;
    upperYBound = anchorTY + 0.1f;
    upperZBound = anchorTZ + 0.1f;

    lowerXBound = anchorTX - 0.1f;
    lowerYBound = anchorTY - 0.1f;
    lowerZBound = anchorTZ - 0.1f;


    if ((hitTX > lowerXBound) && (hitTX < upperXBound)) {
      XInRange = true;
    } else {
      XInRange = false;
      return XInRange;
    }

    if ((hitTY > lowerYBound) && (hitTY < upperYBound)) {
      YInRange = true;
    } else {
      YInRange = false;
      return YInRange;
    }

    if ((hitTZ > lowerZBound) && (hitTZ < upperZBound)) {
      ZInRange = true;
    } else {
      ZInRange = false;
      return ZInRange;
    }

    if ((XInRange) && (YInRange) && (ZInRange)) {
      withinRange = true;
    }


    return withinRange;
  }

  private void translateMatrix(float xDistance, float yDistance, float zDistance) {
    Matrix.translateM(anchorMatrix, 0, xDistance, yDistance, zDistance);
  }

  private void rotateYAxisMatrix(float rotationTheta) {
    if (rotationTheta != 0.0f) {
      anchorMatrix[0] = (float) Math.cos(rotationTheta);
      anchorMatrix[2] = (float) Math.sin(rotationTheta);
      anchorMatrix[5] = 1;
      anchorMatrix[8] = -(float) Math.sin(rotationTheta);
      anchorMatrix[10] = (float) Math.cos(rotationTheta);
      anchorMatrix[15] = 1;
    }
  }
}