package com.magentamause.cosybackend.services.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.PullProgressDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.exceptions.docker.DockerPullImageException;
import java.util.Arrays;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Service for managing Docker images including pulling and verification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerImageManager {

    private final DockerClient client;

    public void ensureImagePresent(
            String image,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback)
            throws DockerPullImageException {
        
        boolean exists = imageExists(image);

        if (!exists) {
            pullImage(image, progressListener, statusUpdater, imagePullStartCallback, imagePullEndCallback);
        }
    }

    private boolean imageExists(String image) {
        return client.listImagesCmd().withImageNameFilter(image).exec().stream()
                .anyMatch(
                        img -> {
                            String[] tags = img.getRepoTags();
                            return tags != null && Arrays.asList(tags).contains(image);
                        });
    }

    private void pullImage(
            String image,
            Consumer<StartEventDto> progressListener,
            Consumer<GameServerDto.GameServerStatus> statusUpdater,
            Consumer<Void> imagePullStartCallback,
            Consumer<Void> imagePullEndCallback)
            throws DockerPullImageException {
        
        imagePullStartCallback.accept(null);
        statusUpdater.accept(GameServerDto.GameServerStatus.PULLING_IMAGE);
        
        try {
            ResultCallback.Adapter<PullResponseItem> callback =
                    new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(PullResponseItem item) {
                            if (progressListener != null) {
                                PullProgressDto.PullProgressDtoBuilder builder =
                                        PullProgressDto.builder()
                                                .status(item.getStatus())
                                                .id(item.getId());

                                if (item.getProgressDetail() != null) {
                                    builder.current(item.getProgressDetail().getCurrent())
                                            .total(item.getProgressDetail().getTotal());
                                }

                                progressListener.accept(
                                        new StartEventDto.PullProgress(builder.build()));
                            }
                        }
                    };
            client.pullImageCmd(image).exec(callback).awaitCompletion();
            imagePullEndCallback.accept(null);
        } catch (Exception e) {
            statusUpdater.accept(GameServerDto.GameServerStatus.FAILED);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DockerPullImageException(image);
        }
    }
}
