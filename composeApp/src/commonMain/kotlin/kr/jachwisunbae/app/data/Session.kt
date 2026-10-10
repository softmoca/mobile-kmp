package kr.jachwisunbae.app.data

import kotlinx.serialization.Serializable

/** 플랫폼 보안 저장소만 이 인터페이스를 구현한다. 비밀번호는 저장하지 않는다. */
interface TokenStore {
    suspend fun read(): String?
    suspend fun write(token: String)
    suspend fun clear()
}

sealed interface AuthSession {
    data object Loading : AuthSession
    data class LoggedOut(val reason: String? = null) : AuthSession
    data class Authenticated(val member: MemberDto) : AuthSession
}

@Serializable
data class MemberDto(val id: Long, val name: String, val passwordProtected: Boolean)

@Serializable
data class LoginMemberDto(val memberId: Long, val name: String, val passwordProtected: Boolean)

@Serializable
data class LoginDto(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val newMember: Boolean,
    val member: LoginMemberDto,
)

@Serializable
internal data class LoginRequest(val nickname: String, val password: String?)

@Serializable
data class FieldErrorDto(val field: String, val reason: String)

class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val errors: List<FieldErrorDto> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
internal data class ApiEnvelope<T>(val code: String, val message: String, val data: T)

@Serializable
internal data class ErrorEnvelope(
    val code: String,
    val message: String,
    val errors: List<FieldErrorDto> = emptyList(),
)
