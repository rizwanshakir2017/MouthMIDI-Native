package com.mouthmidi.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceStatus: TextView
    private lateinit var ccValue: TextView
    private lateinit var ccMeter: MouthMeterView

    private lateinit var cameraExecutor: ExecutorService

    private var faceLandmarker: FaceLandmarker? = null

    private var settings = MouthMidiSettings()

    private var frameCount = 0
    private var mpSubmitted = 0
    private var mpErrors = 0
    private var lastMpError = ""

    private var lastJawOpen = 0f
    private var lastCC = 0

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                faceStatus.text = "● Camera Denied"
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.cameraPreview)
        faceStatus = findViewById(R.id.faceStatus)
        ccValue = findViewById(R.id.ccValue)
        ccMeter = findViewById(R.id.ccMeter)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupFaceLandmarker()


        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    private fun setupFaceLandmarker() {

        val baseOptions =
            BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build()


        val options =
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener(
                    OutputHandler.ResultListener { result, _ ->

                        processFace(result)

                    }
                )
                .build()


        faceLandmarker =
            FaceLandmarker.createFromOptions(
                this,
                options
            )
    }


    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)


        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()


            val preview =
                Preview.Builder()
                    .build()


            preview.setSurfaceProvider(
                previewView.surfaceProvider
            )


            val analyzer =
                ImageAnalysis.Builder()
                    .setOutputImageFormat(
                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                    )
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()


            analyzer.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                processFrame(imageProxy)

            }


            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA


            cameraProvider.unbindAll()


            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                analyzer
            )


        }, ContextCompat.getMainExecutor(this))
    }



    private fun processFrame(
        imageProxy: ImageProxy
    ) {

        frameCount++

        val mediaImage =
            imageProxy.image


        if (mediaImage != null) {

            try {

                val mpImage =
                    MediaImageBuilder(mediaImage)
                        .build()


                mpSubmitted++


                faceLandmarker?.detectAsync(
                    mpImage,
                    System.currentTimeMillis()
                )


            } catch (e: Exception) {

                mpErrors++

                lastMpError =
                    e.message ?: "unknown"

                Log.e(
                    "MouthMIDI",
                    "MediaPipe error: ${e.message}"
                )
            }
        }


        


        imageProxy.close()
    }



    private fun processFace(
        result: FaceLandmarkerResult
    ) {

        runOnUiThread {


            if (result.faceLandmarks().isEmpty()) {

                faceStatus.text =
                    "● No Face"

                updateCC(lastCC)

                return@runOnUiThread
            }


            faceStatus.text =
                "● Face:✅"


            val landmarks =
                result.faceLandmarks()[0]

            Log.d(
                "MouthMIDI",
                "LANDMARK_COUNT=" + landmarks.size

              )
            result.faceBlendshapes().ifPresent { faces ->
                if (faces.isNotEmpty()) {
                    val jaw = faces[0].firstOrNull {
                        it.categoryName() == "jawOpen"
                    }
                    lastJawOpen = jaw?.score() ?: 0f
                }
            }


              val mouthOpen = calibrateJaw(lastJawOpen)



              val cc =
                  (settings.minCC + mouthOpen * (settings.maxCC - settings.minCC))
                      .toInt()
                      .coerceIn(settings.minCC, settings.maxCC)


            updateCC(cc)

        }
    }





    private fun calibrateJaw(value: Float): Float {

        val range = settings.jawOpenCalibration - settings.jawClosedCalibration

        if (range <= 0f) return 0f

        return ((value - settings.jawClosedCalibration) / range)
            .coerceIn(0f, 1f)
    }



    private fun updateCC(
        value:Int
    ){

        lastCC = value

        ccValue.text =
            value
                .toString()
                .padStart(3,'0')


        ccMeter.setValue(value)
    }



    override fun onDestroy() {

        super.onDestroy()

        cameraExecutor.shutdown()

        faceLandmarker?.close()
    }
}
