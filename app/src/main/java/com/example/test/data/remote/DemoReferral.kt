package com.example.test.data.remote

/** Two virtual accounts only. Never changes EntitlementRuntime or a real subscription. */
class DemoReferral(activated:Boolean=false){
    var activated:Boolean=activated
        private set
    val ownBonusDays:Int get()=if(activated)14 else 0
    val friendBonusDays:Int get()=if(activated)14 else 0
    fun activate(code:String):String? {
        if(activated)return "Bu demo hesabında kod zaten kullanıldı."
        return when(code.trim().uppercase(java.util.Locale.ROOT)){
            OWN_CODE->"Kendi kodunu kullanamazsın."
            FRIEND_CODE->{activated=true;null}
            else->"Örnek kod: $FRIEND_CODE"
        }
    }
    companion object {const val OWN_CODE="DEMO-MINE";const val FRIEND_CODE="DEMO-FRIEND"}
}
