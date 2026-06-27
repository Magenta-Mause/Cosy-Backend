package com.magentamause.cosybackend.services.core.gameserver;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.List;

record SecureTarget(
        SecureDirectoryStream<Path> parentDir,
        Path leafName,
        List<DirectoryStream<Path>> toClose) {}
