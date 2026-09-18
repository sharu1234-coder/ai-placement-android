package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.local.LocalAccountDataCleaner
import com.aiplacementos.app.data.remote.AuthApi
import com.aiplacementos.app.data.remote.ForgotPasswordRequestDto
import com.aiplacementos.app.data.remote.LoginRequestDto
import com.aiplacementos.app.data.remote.ResetPasswordRequestDto
import com.aiplacementos.app.data.remote.SignupRequestDto
import com.aiplacementos.app.data.remote.UserDto
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.network.TokenStore

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val localAccountDataCleaner: LocalAccountDataCleaner,
) : AuthRepository {

    override suspend fun signup(email: String, password: String): UserDto =
        authApi.signup(SignupRequestDto(email = email, password = password))

    /**
     * Signing in also decides whether the data already on this device belongs to whoever is
     * signing in.
     *
     * The purge on logout is the primary guarantee; this is the one for when logout never ran
     * -- force-stopped app, killed process, or a phone handed over while still signed in. In
     * any of those, the previous student's notifications and alarms are still here, and
     * without this check the person now signing in would see them.
     *
     * Ordering is deliberate. The purge happens after the server has accepted the credentials
     * but before the tokens are saved, so: a failed login cannot wipe the rightful owner's data
     * (the call throws first), and no screen can observe a half-cleared database, because
     * nothing renders account data until there is a token. The fingerprint is written last, so
     * a crash anywhere in the middle leaves the old marker in place and the next login purges
     * again -- failing towards deleting too much rather than too little.
     */
    override suspend fun login(email: String, password: String) {
        val tokens = authApi.login(LoginRequestDto(email = email, password = password))

        val fingerprint = TokenStore.fingerprintOf(email)
        if (tokenStore.accountFingerprint() != fingerprint) {
            localAccountDataCleaner.purge()
        }

        tokenStore.save(tokens.accessToken, tokens.refreshToken)
        tokenStore.setAccountFingerprint(fingerprint)
    }

    override suspend fun isLoggedIn(): Boolean = tokenStore.currentAccessToken() != null

    /**
     * Ends the session on this device completely: scheduled alarms cancelled, account-specific
     * local tables emptied, then credentials cleared.
     *
     * Before Stage 6 this cleared the tokens alone, so the next student to sign in on the same
     * device inherited the previous one's notification history and alarms -- and deleting the
     * account did not help, because the server erased its copy and the device kept its own.
     * Account deletion routes through here too (see SettingsViewModel), so both paths get the
     * same cleanup from one place rather than two that can drift apart.
     *
     * The purge runs before the tokens are cleared, and this function suspends until it is
     * done, so the caller cannot navigate away and leave it half-finished. If the purge throws,
     * the tokens deliberately stay put: remaining signed in is recoverable and the student can
     * retry, whereas clearing credentials over a database still holding the previous account's
     * data would strand it there with no logged-in session left to clean it up.
     */
    override suspend fun logout() {
        localAccountDataCleaner.purge()
        tokenStore.clear()
    }

    override suspend fun forgotPassword(email: String): String =
        authApi.forgotPassword(ForgotPasswordRequestDto(email = email)).message

    override suspend fun resetPassword(email: String, code: String, newPassword: String): String =
        authApi.resetPassword(
            ResetPasswordRequestDto(email = email, code = code, newPassword = newPassword)
        ).message
}
