package ai.pleos.coach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ai.pleos.coach.ui.CoachApp

class MainActivity : ComponentActivity() {
    private val model: CoachViewModel by viewModels()
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.onPermissionsChanged()
    }
    private val routeImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { it?.let(model::importRoute) }
          .onFailure { model.showMessage("경로 파일을 읽지 못했어요: "+it.message) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.requestRouteImport = { routeImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        setContent { CoachApp(model.uiState, model.actions) }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            val permissions = arrayOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN",
                "pleos.car.permission.CAR_DRIVE", "pleos.car.permission.CAR_LIGHTS", "pleos.car.permission.NAVI_ROUTE")
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (permissions.isNotEmpty()) permissionRequest.launch(permissions.toTypedArray())
        }
    }
    override fun onStart() { super.onStart(); model.onHostStart() }
    override fun onStop() { model.onHostStop(); super.onStop() }
}
