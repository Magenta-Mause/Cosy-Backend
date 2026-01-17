package com.magentamause.cosybackend.exceptions.docker;

import lombok.Getter;

public class DockerPullImageException extends Exception {
    @Getter
    private final String imageName;
    public DockerPullImageException(String imageName) {
        super("Cant pull docker image: " + imageName);
        this.imageName = imageName;
    }
}
