package com.moviles.ark.data.local

import android.util.Log
import androidx.collection.LruCache
import com.moviles.ark.domain.models.WishModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

//cache en memoria (ram) de los deseos del farol: un farol encendido es un deseo que esta aqui
//es un LruCache (least recently used): guarda maximo 8 deseos y, si se llena,
//saca el que hace mas tiempo no se usa; ese farol se apaga en el cielo
//leer un deseo de aqui no necesita ir a room
//@Synchronized: la usan varios hilos (IO) a la vez, asi que solo uno puede cambiarla a la vez
class WishMemoryCache(
    //cuantos deseos caben, o sea cuantos faroles pueden estar encendidos al mismo tiempo
    maxLanterns: Int = WishModel.MAX_LIT_LANTERNS,
    //donde se escriben los mensajes de la cache; por defecto en logcat
    private val log: (String) -> Unit = { message -> Log.d(TAG, message) }
) {
    //cuantas veces el deseo estaba en la cache (hit) y cuantas hubo que ir a room (miss)
    private var hits = 0
    private var misses = 0

    //ids de los deseos que estan en la cache; la pantalla los usa para saber que faroles van encendidos
    private val litWishIds = MutableStateFlow<Set<String>>(emptySet())

    //el LruCache de verdad: la llave es el id del deseo y el valor es el deseo
    private val cache = object : LruCache<String, WishModel>(maxLanterns) {
        //se llama cada vez que sale un deseo de la cache
        //evicted = true cuando salio porque la cache estaba llena (no porque se borro a mano)
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: WishModel, newValue: WishModel?) {
            if (evicted) {
                log("evicted id=$key: its lantern turns off")
            }
        }
    }

    //busca un deseo; null si no esta (hay que ir a room)
    //si esta, pasa a ser el mas recientemente usado
    @Synchronized
    fun get(wishId: String): WishModel? {
        val wish = cache.get(wishId)
        if (wish != null) {
            hits++
            log("hit id=$wishId (hits=$hits, misses=$misses)")
        } else {
            misses++
            log("miss id=$wishId (hits=$hits, misses=$misses)")
        }
        return wish
    }

    //mete un deseo a la cache (su farol se enciende)
    //si ya habia 8, el LruCache saca solo el que hace mas tiempo no se usa
    @Synchronized
    fun put(wish: WishModel) {
        cache.put(wish.id, wish)
        log("put id=${wish.id} (${cache.size()} / ${cache.maxSize()} lit)")
        updateLitWishIds()
    }

    //despues de editar un deseo: si estaba en la cache se cambia por la version nueva
    //asi el cielo nunca muestra el texto viejo; si no estaba, no se mete
    @Synchronized
    fun replaceIfPresent(editedWish: WishModel) {
        val isInCache = cache.snapshot().containsKey(editedWish.id)
        if (isInCache) {
            cache.put(editedWish.id, editedWish)
            log("replaced id=${editedWish.id} with the edited text")
        }
    }

    //saca un deseo de la cache (por ejemplo, porque se borro); su farol se apaga
    @Synchronized
    fun remove(wishId: String) {
        cache.remove(wishId)
        updateLitWishIds()
    }

    //vacia toda la cache (por ejemplo, si entra otro usuario)
    @Synchronized
    fun clear() {
        cache.evictAll()
        updateLitWishIds()
    }

    //true si la cache no tiene ningun deseo
    fun isEmpty(): Boolean {
        return cache.size() == 0
    }

    //ids de los faroles encendidos; se actualiza solo cada vez que la cache cambia
    fun observeLitWishIds(): Flow<Set<String>> {
        return litWishIds
    }

    //le avisa a la pantalla que faroles estan encendidos ahora
    //snapshot() es una copia de lo que hay en la cache, sin cambiar el orden de uso
    private fun updateLitWishIds() {
        litWishIds.value = cache.snapshot().keys.toSet()
    }

    companion object {
        const val TAG = "WishCache"
    }
}
