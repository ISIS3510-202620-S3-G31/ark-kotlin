package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//aqui se usa room: regalos que este telefono envio ("Regalos enviados" en la pantalla de amigos)
//viven solo en el telefono del remitente: si se leyeran de firestore el regalo desapareceria al aceptarlo o rechazarlo
//y el remitente sabria que hubo respuesta; aqui siempre dice "Sent ✓"
@Entity(
    tableName = "sent_gifts",
    indices = [Index(value = ["userId"])]
)
data class SentGiftEntity(
    //id del regalo: shared_cookies/{shareId} y shared/{shareId}.jpg
    @PrimaryKey
    val shareId: String,
    //uid del remitente (la cuenta con sesion cuando se envio)
    val userId: String,
    val toUid: String,
    val toUsername: String,
    val title: String,
    val hasImage: Boolean,
    val sentAt: Long,
    //a los 7 dias el regalo vence y ya no se puede cancelar
    val expiresAt: Long,
)
