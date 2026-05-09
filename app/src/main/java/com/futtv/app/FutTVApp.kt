package com.futtv.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.futtv.app.data.repository.SportsRepository
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FutTVApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Coil usa nuestro mismo OkHttpClient con SSL trust-all
        // para que las imágenes de TheSportsDB carguen correctamente
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(httpClient)
                .crossfade(true)
                .build()
        )
    }

    val httpClient: OkHttpClient by lazy {
        // TheSportsDB y los sitios de streaming tienen certificados SSL problemáticos
        // en el trust store de Android TV — se acepta cualquier cert para que funcione.
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslCtx = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }

        OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "es-AR,es;q=0.9,en;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val repository: SportsRepository by lazy {
        SportsRepository(httpClient)
    }
}
