package com.magentamause.cosybackend.services.core.gameserver;

import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;

record SecureRoot(SecureDirectoryStream<Path> sds, boolean secure) {}
