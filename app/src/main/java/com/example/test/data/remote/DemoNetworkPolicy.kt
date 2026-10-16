package com.example.test.data.remote

import java.net.URI

object DemoNetworkPolicy {
    fun allowed(value:String):Boolean=runCatching {
        val url=URI(value)
        url.scheme=="http" && url.host in setOf("localhost","127.0.0.1","10.0.2.2") &&
            url.userInfo==null && url.fragment==null && url.port in -1..65535
    }.getOrDefault(false)
}
