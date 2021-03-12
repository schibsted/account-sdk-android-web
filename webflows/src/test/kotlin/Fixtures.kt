import com.schibsted.account.android.webflows.client.ClientConfiguration
import com.schibsted.account.android.webflows.token.IdTokenClaims
import com.schibsted.account.android.webflows.token.UserTokens
import java.net.URL

internal object Fixtures {
    val clientConfig = ClientConfiguration(
        URL("https://issuer.example.com"),
        "client1",
        "com.example.client://login"
    )
    val idTokenClaims = IdTokenClaims(
        clientConfig.issuer,
        "userUuid",
        "12345",
        listOf(clientConfig.clientId),
        10,
        "testNonce",
        null
    )
    val userTokens = UserTokens("accessToken", "refreshToken", "idToken", idTokenClaims)
}
