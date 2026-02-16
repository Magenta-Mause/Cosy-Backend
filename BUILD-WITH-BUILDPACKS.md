# Building with Spring Boot Buildpacks

This project now uses Spring Boot Buildpacks instead of a Dockerfile for container image creation.

## Why Buildpacks?

- **Full native library support**: Includes all dependencies needed by Reactor Netty (HTTP/3/QUIC)
- **Better layer caching**: Dependencies and application code are in separate layers
- **Production-ready**: Uses Paketo buildpacks with Ubuntu Jammy base
- **No Dockerfile maintenance**: Spring Boot handles image creation

## Building the Image

```bash
./mvnw spring-boot:build-image
```

This creates an OCI image: `cosy-backend:0.0.1-SNAPSHOT`

## Running Locally

```bash
docker run -p 8080:8080 cosy-backend:0.0.1-SNAPSHOT
```

## Deployment

For deployment platforms (Kubernetes, Cloud Run, etc.), either:

1. **Push to registry**:
   ```bash
   docker tag cosy-backend:0.0.1-SNAPSHOT your-registry/cosy-backend:latest
   docker push your-registry/cosy-backend:latest
   ```

2. **Use in CI/CD**: Run `mvn spring-boot:build-image` in your pipeline

## Configuration Options

The image name is configured in `pom.xml`:
```xml
<image>
    <name>cosy-backend:${project.version}</name>
</image>
```

To customize, see [Spring Boot Maven Plugin docs](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/#build-image).
