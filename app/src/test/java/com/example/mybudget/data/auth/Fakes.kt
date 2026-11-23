package com.example.mybudget.data.auth

import com.example.mybudget.data.auth.local.SessionStorage
import com.example.mybudget.data.auth.remote.AuthApi
import com.example.mybudget.data.auth.remote.AuthResponse
import com.example.mybudget.data.auth.remote.LoginRequest
import com.example.mybudget.data.auth.remote.LogoutRequest
import com.example.mybudget.data.auth.remote.RefreshRequest
import com.example.mybudget.data.auth.remote.RegisterRequest
import com.example.mybudget.data.auth.remote.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

class InMemorySessionStorage(initial: AuthSession? = null) : SessionStorage {
    private val state = MutableStateFlow(initial)
    override val session: Flow<AuthSession?> = state
    override suspend fun read() = state.value
    override suspend fun save(session: AuthSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

class FakeAuthApi : AuthApi {
    var loginResult: () -> AuthResponse = { authResponse() }
    var registerResult: () -> AuthResponse = { authResponse() }
    var refreshResult: () -> AuthResponse = { authResponse(access = "access-2", refresh = "refresh-2") }
    var logoutResult: () -> Unit = {}

    var loginCalls = 0
    var refreshCalls = 0
    var lastRegister: RegisterRequest? = null
    var lastLogoutAuthorization: String? = null

    override suspend fun login(body: LoginRequest): AuthResponse { loginCalls++; return loginResult() }
    override suspend fun register(body: RegisterRequest): AuthResponse { lastRegister = body; return registerResult() }
    override suspend fun refresh(body: RefreshRequest): AuthResponse { refreshCalls++; return refreshResult() }
    override suspend fun logout(authorization: String, body: LogoutRequest) {
        lastLogoutAuthorization = authorization
        logoutResult()
    }
}

val testUser = User(id = "u1", email = "jane@example.com", displayName = "Jane")

fun authResponse(access: String = "access-1", refresh: String = "refresh-1") = AuthResponse(
    accessToken = access,
    refreshToken = refresh,
    user = UserDto(testUser.id, testUser.email, testUser.displayName),
)

fun httpError(code: Int, body: String = "{}") = HttpException(
    Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
)
