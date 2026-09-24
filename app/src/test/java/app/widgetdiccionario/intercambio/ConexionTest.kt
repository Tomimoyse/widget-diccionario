package app.widgetdiccionario.intercambio

import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Intercambio por sockets de verdad, entre dos puntas en esta misma máquina. */
class ConexionTest {

    @Test
    fun dosPuntasIntercambianPorSocket() {
        val anfitrion = Anfitrion()
        val hilos = Executors.newFixedThreadPool(2)
        val ofreceAna = listOf(PalabraOfrecida("anemoia", "f.", "Nostalgia de un tiempo no vivido."))
        val ofreceBeto = listOf(PalabraOfrecida("ñandú", "m.", "Ave corredora."))

        val ana = hilos.submit<Pair<String, List<PalabraOfrecida>>> {
            anfitrion.use {
                val sesion = Sesion(it.esperarConexion(esperaMs = 5_000), anfitrion = true)
                val saludo = sesion.saludar("Ana")
                sesion.ofrecer(ofreceAna)
                saludo.codigo to sesion.esperarOferta()
            }
        }
        val beto = hilos.submit<Pair<String, List<PalabraOfrecida>>> {
            val sesion = Sesion(Visitante.conectar(InetAddress.getLoopbackAddress(), anfitrion.puerto), false)
            val saludo = sesion.saludar("Beto")
            sesion.ofrecer(ofreceBeto)
            saludo.codigo to sesion.esperarOferta()
        }

        val (codigoAna, recibeAna) = ana.get(10, TimeUnit.SECONDS)
        val (codigoBeto, recibeBeto) = beto.get(10, TimeUnit.SECONDS)
        hilos.shutdown()

        assertEquals(codigoAna, codigoBeto)
        assertEquals(ofreceBeto, recibeAna)
        assertEquals(ofreceAna, recibeBeto)
    }

    @Test
    fun soloSeAceptanDireccionesDeLaRedLocal() {
        listOf("192.168.0.5", "10.0.0.7", "172.16.3.1", "127.0.0.1", "169.254.1.2", "fd00::1")
            .forEach { assertTrue(it, RedLocal.esDeRedLocal(InetAddress.getByName(it))) }
        listOf("8.8.8.8", "1.1.1.1", "200.45.12.9", "2001:4860:4860::8888")
            .forEach { assertFalse(it, RedLocal.esDeRedLocal(InetAddress.getByName(it))) }
    }

    @Test
    fun elCodigoDeConexionSeArmaYSeVuelveALeer() {
        val propia = InetAddress.getByName("192.168.0.14") as java.net.Inet4Address
        val codigo = RedLocal.codigoDeConexion(InetAddress.getByName("192.168.0.23") as java.net.Inet4Address, 41234)
        assertEquals("23-41234", codigo)

        val (direccion, puerto) = RedLocal.desdeCodigo(codigo, propia)!!
        assertEquals("192.168.0.23", direccion.hostAddress)
        assertEquals(41234, puerto)

        listOf("", "23", "999-41234", "23-80", "hola-mundo").forEach { assertNull(it, RedLocal.desdeCodigo(it, propia)) }
    }
}
