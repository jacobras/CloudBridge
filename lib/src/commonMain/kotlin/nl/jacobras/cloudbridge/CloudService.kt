package nl.jacobras.cloudbridge

public interface CloudService {
    public suspend fun listFiles(): List<String>
}