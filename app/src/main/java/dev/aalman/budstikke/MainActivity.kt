package dev.aalman.budstikke

import android.app.role.RoleManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aalman.budstikke.data.ConversationRepository
import dev.aalman.budstikke.ui.ConversationListScreen
import dev.aalman.budstikke.ui.RoleScreen
import dev.aalman.budstikke.ui.theme.BudstikkeTheme

class MainActivity : ComponentActivity() {

    private val roleManager by lazy { getSystemService(RoleManager::class.java) }
    private var isDefaultSmsApp by mutableStateOf(false)

    private val requestRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshRole()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshRole()
        val repository = ConversationRepository(this)
        val roleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_SMS)

        setContent {
            BudstikkeTheme {
                if (isDefaultSmsApp) {
                    val flow = remember { repository.conversations() }
                    val conversations by flow.collectAsStateWithLifecycle(initialValue = null)
                    ConversationListScreen(conversations)
                } else {
                    RoleScreen(roleAvailable) {
                        requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The role can be taken away in system settings while the app is in the background.
        refreshRole()
    }

    private fun refreshRole() {
        isDefaultSmsApp = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }
}
