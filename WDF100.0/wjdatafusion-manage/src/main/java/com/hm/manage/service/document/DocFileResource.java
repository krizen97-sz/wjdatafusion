package com.hm.manage.service.document;

import java.nio.file.Path;

public record DocFileResource(Path path, String filename, String fileType)
{
}
