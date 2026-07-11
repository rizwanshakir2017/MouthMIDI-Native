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
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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
    private lateinit var debugInfo: TextView

    private lateinit var cameraExecutor: ExecutorService

    private var faceLandmarker: FaceLandmarker? = null

    private var lastMouthHeight = 0f
    private var lastMouthWidth = 0f
    private var lastHeight13_14 = 0f
    private var lastHeight0_17 = 0f
    private var frameCount = 0
    private var mpSubmitted = 0
    private var mpErrors = 0
    private var lastMpError = ""


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
        debugInfo = findViewById(R.id.debugInfo)

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

                updateCC(0)

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


            val mouthOpen =
                calculateMouthOpening(
                    landmarks
                )

            debugInfo.text =
                "Landmarks: ${landmarks.size}\n" +
                "H13-14: %.4f\n".format(lastHeight13_14) +
                "H0-17: %.4f\n".format(lastHeight0_17) +
                "Width: %.4f\n".format(lastMouthWidth) +
                "Ratio: %.4f\n".format(if (lastMouthWidth > 0f) lastMouthHeight / lastMouthWidth else 0f) +

                "CC: ${((mouthOpen * 127f).toInt())}"


            val cc =
                (mouthOpen * 127f)
                    .toInt()
                    .coerceIn(0,127)


            updateCC(cc)

        }
    }



    private fun calculateMouthOpening(
        landmarks: List<NormalizedLandmark>
    ): Float {

        Log.d("MOUTH","13=" + landmarks[13].x() + "," + landmarks[13].y())

        val mouthIds = listOf(13,14,61,291,0,17,37,267,78,308,81,311,82,312,87,317)
        mouthIds.forEach { i ->
            Log.d("MOUTHMAP", i.toString() + "=" + landmarks[i].x() + "," + landmarks[i].y())
        }

        val upper =
            landmarks[13].y()

        Log.d("MOUTH","14=" + landmarks[14].x() + "," + landmarks[14].y())

        val lower =
            landmarks[14].y()


        Log.d("MOUTH","61=" + landmarks[61].x() + "," + landmarks[61].y())

        val left =
            landmarks[61].x()

        Log.d("MOUTH","291=" + landmarks[291].x() + "," + landmarks[291].y())

        val right =
            landmarks[291].x()


        val height =
            kotlin.math.abs(lower - upper)

        val width =
            kotlin.math.abs(right - left)


        lastMouthHeight = height
        lastMouthWidth = width
        lastHeight13_14 = kotlin.math.abs(landmarks[14].y() - landmarks[13].y())
        lastHeight0_17 = kotlin.math.abs(landmarks[17].y() - landmarks[0].y())


        val normalized =
            ((height - 0.001f) /
            (0.030f - 0.001f))
                .coerceIn(0f,1f)


        return normalized
    }



    private fun updateCC(
        value:Int
    ){

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
