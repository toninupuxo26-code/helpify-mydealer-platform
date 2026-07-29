package app.shared.core

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

abstract class BaseAuthActivity : AppCompatActivity() {
    protected abstract val productConfig: ProductConfig
    protected abstract fun openDashboard(user: ApiUser)

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var repository: AuthRepository
    private lateinit var store: SessionStore
    private lateinit var title: TextView
    private lateinit var screenTitle: TextView
    private lateinit var status: TextView
    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var roleSpinner: Spinner
    private lateinit var codeInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var submitButton: Button

    private var mode = Mode.LOGIN
    private var recoveryCodeRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        repository = AuthRepository(productConfig.apiBaseUrl)
        store = SessionStore(this, productConfig.productName)
        bindViews()
        setupRoles()
        setupModes()
        showMode(Mode.LOGIN)
        restoreSession()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        title = findViewById(R.id.productTitle)
        screenTitle = findViewById(R.id.screenTitle)
        status = findViewById(R.id.statusText)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        roleSpinner = findViewById(R.id.roleSpinner)
        codeInput = findViewById(R.id.codeInput)
        newPasswordInput = findViewById(R.id.newPasswordInput)
        submitButton = findViewById(R.id.submitButton)
        title.text = productConfig.productName
        findViewById<TextView>(R.id.demoHint).text = productConfig.demoAccounts.joinToString(
            prefix = "Демонстрационные аккаунты:\n",
            separator = "\n"
        ) { "${it.title}: ${it.email} / ${it.password}" }
    }

    private fun setupRoles() {
        roleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            productConfig.roles.map { it.title }
        )
    }

    private fun setupModes() {
        findViewById<Button>(R.id.loginModeButton).setOnClickListener { showMode(Mode.LOGIN) }
        findViewById<Button>(R.id.registerModeButton).setOnClickListener { showMode(Mode.REGISTER) }
        findViewById<Button>(R.id.recoveryModeButton).setOnClickListener { showMode(Mode.RECOVERY) }
        submitButton.setOnClickListener { submit() }
    }

    private fun showMode(newMode: Mode) {
        mode = newMode
        recoveryCodeRequested = false
        status.text = ""
        nameInput.visibility = if (mode == Mode.REGISTER) View.VISIBLE else View.GONE
        roleSpinner.visibility = if (mode == Mode.REGISTER) View.VISIBLE else View.GONE
        passwordInput.visibility = if (mode == Mode.RECOVERY) View.GONE else View.VISIBLE
        codeInput.visibility = View.GONE
        newPasswordInput.visibility = View.GONE
        screenTitle.text = when (mode) {
            Mode.LOGIN -> getString(R.string.core_login)
            Mode.REGISTER -> getString(R.string.core_register)
            Mode.RECOVERY -> getString(R.string.core_recovery)
        }
    }

    private fun submit() {
        when (mode) {
            Mode.LOGIN -> login()
            Mode.REGISTER -> register()
            Mode.RECOVERY -> if (recoveryCodeRequested) resetPassword() else requestRecovery()
        }
    }

    private fun login() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) return showError("Введите email и пароль")
        busy(true)
        executor.execute {
            val (result, session) = repository.login(email, password)
            runOnUiThread {
                busy(false)
                if (session != null) { store.save(session); openDashboard(session.user); finish() }
                else showError(result.message)
            }
        }
    }

    private fun register() {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val role = productConfig.roles[roleSpinner.selectedItemPosition].value
        if (name.length < 2 || email.isBlank() || password.length < 6) {
            return showError("Проверьте имя, email и пароль не короче 6 символов")
        }
        busy(true)
        executor.execute {
            val (result, session) = repository.register(name, email, password, role)
            runOnUiThread {
                busy(false)
                if (session != null) { store.save(session); openDashboard(session.user); finish() }
                else showError(result.message)
            }
        }
    }

    private fun requestRecovery() {
        val email = emailInput.text.toString().trim()
        if (email.isBlank()) return showError("Введите email")
        busy(true)
        executor.execute {
            val (result, demoCode) = repository.forgot(email)
            runOnUiThread {
                busy(false)
                if (result.successful) {
                    recoveryCodeRequested = true
                    codeInput.visibility = View.VISIBLE
                    newPasswordInput.visibility = View.VISIBLE
                    status.text = if (demoCode != null) "Демо-код: $demoCode" else result.message
                } else showError(result.message)
            }
        }
    }

    private fun resetPassword() {
        val email = emailInput.text.toString().trim()
        val code = codeInput.text.toString().trim()
        val newPassword = newPasswordInput.text.toString()
        if (code.length != 6 || newPassword.length < 6) return showError("Проверьте код и новый пароль")
        busy(true)
        executor.execute {
            val result = repository.reset(email, code, newPassword)
            runOnUiThread {
                busy(false)
                if (result.successful) {
                    status.text = result.message
                    showMode(Mode.LOGIN)
                    emailInput.setText(email)
                } else showError(result.message)
            }
        }
    }

    private fun restoreSession() {
        val token = store.token() ?: return
        status.text = "Проверка сохранённой сессии…"
        executor.execute {
            val (result, user) = repository.me(token)
            runOnUiThread {
                if (user != null) { store.save(AuthSession(token, user)); openDashboard(user); finish() }
                else { store.clear(); status.text = if (result.statusCode == 401) "Сессия завершена" else result.message }
            }
        }
    }

    private fun busy(enabled: Boolean) {
        submitButton.isEnabled = !enabled
        status.text = if (enabled) getString(R.string.core_loading) else ""
    }

    private fun showError(message: String) {
        status.text = message
    }

    private enum class Mode { LOGIN, REGISTER, RECOVERY }
}
