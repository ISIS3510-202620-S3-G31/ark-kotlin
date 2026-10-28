package com.moviles.ark.domain.exceptions

//errores de autenticacion que entiende el dominio; el repositorio traduce los de firebase a estos
//asi los viewmodels no dependen de las clases de firebase

//correo o clave incorrectos (o un correo con formato que el servidor no acepta)
class InvalidCredentialsException(cause: Throwable? = null) : Exception("Invalid email or password", cause)

//ya existe una cuenta con ese correo
class EmailAlreadyRegisteredException(cause: Throwable? = null) : Exception("This email is already registered", cause)
