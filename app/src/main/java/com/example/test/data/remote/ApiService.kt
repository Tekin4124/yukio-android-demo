package com.example.test.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    // Response sarmalayici: backend parser kaynagini (backend/ai/cache)
    // X-Parse-Source basliginda gonderiyor; govdeyle birlikte basliga da
    // erisebilmek icin Response<T> gerekiyor.
    @POST("parse-ilan")
    suspend fun parseIlan(@Body request: ParseRequest): Response<ParseResponse>

    // Kimliksiz sample etkileşimi. sampleId gövdede taşınır (path'te değil) ki
    // proxy access loglarına yazılmasın; sunucu tarafı bu yolu loglamaz.
    @POST("v1/parser-samples/events")
    suspend fun postParserSampleEvent(
        @Body request: ParserSampleEventRequest
    ): Response<Unit>
}
