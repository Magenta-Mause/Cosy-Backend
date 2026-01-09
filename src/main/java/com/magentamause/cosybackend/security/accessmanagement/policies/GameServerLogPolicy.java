package com.magentamause.cosybackend.security.accessmanagement.policies;

import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.repositories.GameServerRepository;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameServerLogPolicy implements AccessPolicy {

	private final GameServerRepository gameServerRepository;

	@Override
	public Resource resource() {
		return Resource.GAME_SERVER_LOG;
	}

	@Override
	public boolean can(UserEntity user, Action action, Object referenceId) {
		if (user.getRole().isAdmin()) {
			return true;
		}

		GameServerEntity gameServerEntity =
				gameServerRepository
						.findById((String) referenceId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND,
												"Game server with uuid " + referenceId + " not found"));

		return switch (action) {
			case READ, DELETE, UPDATE -> gameServerEntity
					.getOwner()
					.getUuid()
					.equals(user.getUuid());
			default -> throw new IllegalStateException("Unexpected value: " + action);
		};
	}
}
