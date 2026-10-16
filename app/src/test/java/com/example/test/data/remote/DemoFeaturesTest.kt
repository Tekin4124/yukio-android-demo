package com.example.test.data.remote

import org.junit.Assert.*
import org.junit.Test

class DemoFeaturesTest {
    private val sample=DemoListingParser.SAMPLE
    @Test fun parserAcceptsOnlyDocumentedRoutesAndSyntheticContacts(){
        val cards=DemoListingParser.parse(sample)
        assertEquals(2,cards.size);assertEquals("00000000001",cards[0].telefon)
        assertEquals("18000",cards[0].fiyat)
        assertNull(DemoListingParser.parse("İSTANBUL -> ANKARA | TIR | 0555 000 00 01")[0].telefon)
        assertNull(normalizeManualPhone("0555 000 00 01"))
        assertNull(normalizeManualPhone("+905550000001"))
        assertNull(normalizeManualPhone("0000 000 00 00"))
        assertEquals("00000000001",normalizeManualPhone("0000 000 00 01"))
        assertThrows(ListingUserError::class.java){DemoListingParser.parse("İSTANBUL -> UNKNOWN")}
    }
    @Test fun previewStaysPrivateUntilConfirmationAndDuplicateMessagesAreSkipped(){
        val engine=DemoListingEngine();val a=engine.preview(sample);val b=engine.preview(sample)
        assertTrue(engine.list().isEmpty())
        assertEquals(2,engine.publish(a,listOf(0,1)).listings.size)
        val repeated=engine.publish(b,listOf(0,1));assertEquals(0,repeated.listings.size);assertEquals(2,repeated.alreadyPublishedCount)
        assertEquals(2,engine.list().size)
        assertEquals(2,engine.publish(a,listOf(0,1)).listings.size)
        assertEquals(2,engine.list().size)
    }
    @Test fun missingPhoneBlocksPublicationAndPreviewCanBeCorrected(){
        val engine=DemoListingEngine();val draft=engine.preview("İSTANBUL -> ANKARA | TIR")
        assertThrows(ListingUserError::class.java){engine.publish(draft,listOf(0))};assertTrue(engine.list().isEmpty())
        val fixed=draft.copy(cards=draft.cards.map{it.copy(ilan=it.ilan.withListingFields(mapOf("telefon" to "0000 000 00 01","fiyat" to "42000")))})
        assertEquals("42000",engine.publish(fixed,listOf(0)).listings[0].ilan.fiyat)
    }
    @Test fun ownerEditsKeepExpiryAndRejectStaleOrDuplicateChanges(){
        var now=1000000L;val engine=DemoListingEngine{now}
        val draft=engine.preview(sample);val items=engine.publish(draft,listOf(0,1)).listings
        now+=1000
        engine.update(items[0],mapOf("fiyat" to "20000"));val edited=engine.list().first{it.id==items[0].id}
        assertEquals(items[0].expiresAtMillis,edited.expiresAtMillis)
        assertThrows(ListingUserError::class.java){engine.update(items[0],mapOf("fiyat" to "1"))}
        assertThrows(ListingUserError::class.java){engine.update(items[1],edited.ilan.editableFields())}
        assertThrows(ListingUserError::class.java){engine.update(edited,mapOf("telefon" to "05550000001"))}
    }
    @Test fun restoredLocalDataStillBlocksDuplicatesAndExpires(){
        var now=1000000L;val original=DemoListingEngine{now}
        val draft=original.preview(sample);original.publish(draft,listOf(0,1))
        val restored=DemoListingEngine.restore(original.snapshot()){now}
        assertEquals(2,restored.list().size)
        assertEquals(2,restored.publish(restored.preview(sample),listOf(0,1)).alreadyPublishedCount)
        now+=86400001;assertTrue(restored.list().isEmpty())
        assertEquals(2,restored.publish(restored.preview(sample),listOf(0,1)).listings.size)
        restored.clear();assertTrue(restored.list().isEmpty())
    }
    @Test fun publicationCapCountsNewCardsAndResetsAfterOneMinute(){
        var now=1000000L;val engine=DemoListingEngine{now}
        for(batch in 0..7){
            val draft=engine.preview(sample.lines()[0])
            val cards=(0 until if(batch==7)10 else 20).map{index->draft.cards[0].copy(index=index,ilan=draft.cards[0].ilan.withListingFields(mapOf("fiyat" to "${batch*20+index+1}")))}
            // Prepare valid distinct cards through the actual fixture grammar.
            val actual=engine.preview(cards.joinToString("\n"){"İSTANBUL -> ANKARA | TIR | ${it.ilan.fiyat} TL | 0000 000 00 01"})
            assertEquals(cards.size,engine.publish(actual,cards.indices.toList()).listings.size)
        }
        val extra=engine.preview("İSTANBUL -> ANKARA | TIR | 99999 TL | 0000 000 00 01")
        assertThrows(ListingUserError::class.java){engine.publish(extra,listOf(0))}
        now+=60000;assertEquals(1,engine.publish(extra,listOf(0)).listings.size)
    }
    @Test fun virtualReferralCreditsBothProfilesExactlyOnceWithoutRealAccess(){
        val demo=DemoReferral();assertNotNull(demo.activate(DemoReferral.OWN_CODE));assertFalse(demo.activated)
        assertNotNull(demo.activate("REAL-UNKNOWN"));assertNull(demo.activate(" demo-friend "))
        assertEquals(14,demo.ownBonusDays);assertEquals(14,demo.friendBonusDays)
        assertNotNull(demo.activate(DemoReferral.FRIEND_CODE));assertEquals(14,demo.ownBonusDays)
    }
    @Test fun productionUrlsAndCredentialOrRedirectTargetsAreNotAllowed(){
        for(url in listOf("https://example.com/","http://example.com/","http://localhost.attacker.invalid/","http://localhost@attacker.invalid/","http://user@localhost/","file:///etc/passwd","http://localhost/#secret"))assertFalse(url,DemoNetworkPolicy.allowed(url))
        for(url in listOf("http://127.0.0.1:3000/parse-ilan","http://localhost:3001/health","http://10.0.2.2:3000/"))assertTrue(url,DemoNetworkPolicy.allowed(url))
    }
}
