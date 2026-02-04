package com.magentamause.cosybackend.services.technical;

import com.magentamause.cosybackend.exceptions.RconBadAuthorizationException;
import com.magentamause.cosybackend.exceptions.RconException;
import java.io.IOException;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.glavo.rcon.AuthenticationException;
import org.glavo.rcon.Rcon;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RCONService {

    public void sendCommand(
            int port, String password, String command, Consumer<String> responseCallback)
            throws RconBadAuthorizationException, RconException {
        try (Rcon rcon = new Rcon("127.0.0.1", port, password)) {
            String response = rcon.command(command);
            responseCallback.accept(response);
        } catch (IOException e) {
            throw new RconException(e.getMessage());
        } catch (AuthenticationException e) {
            throw new RconBadAuthorizationException(e.getMessage());
        }
    }
}
