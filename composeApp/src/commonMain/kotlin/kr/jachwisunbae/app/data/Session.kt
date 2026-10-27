package kr.jachwisunbae.app.data

import kotlinx.serialization.Serializable

/** 플랫폼은 버전이 있는 세션 문자열을 원자적으로 암호화 저장한다. Access Token은 저장하지 않는다. */
interface TokenStore {
    suspend fun read(): String?
    suspend fun write(token: String)
    suspend fun clear()
}

sealed interface AuthSession {
    data object Loading : AuthSession
    data class LoggedOut(
        val reason: String? = null,
        val canRetry: Boolean = false,
        val requiresNewSessionConfirmation: Boolean = false,
    ) : AuthSession
    data class Authenticated(val member: MemberDto) : AuthSession
}

@Serializable
data class MemberDto(val id: Long)

@Serializable
data class LoginMemberDto(val memberId: Long)

@Serializable
data class LoginDto(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val newMember: Boolean,
    val member: LoginMemberDto,
) {
    override fun toString(): String = "LoginDto(accessToken=<redacted>, tokenType=$tokenType, expiresIn=$expiresIn, newMember=$newMember, member=$member)"
}

@Serializable
internal data class StoredSession(
    val version: Int = 2,
    val origin: String,
    val memberId: Long? = null,
    val refreshToken: String? = null,
    val requiresNewSessionConfirmation: Boolean = false,
) {
    override fun toString(): String = "StoredSession(version=$version, origin=$origin, memberId=$memberId, refreshToken=<redacted>, requiresNewSessionConfirmation=$requiresNewSessionConfirmation)"
}

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
