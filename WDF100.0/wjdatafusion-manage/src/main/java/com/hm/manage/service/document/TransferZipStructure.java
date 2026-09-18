package com.hm.manage.service.document;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipException;
import com.hm.common.exception.ServiceException;

/**
 * Bounded metadata checks for transfer-only ZIPs. No member is decompressed or
 * decrypted. In particular, a parser's ability to extract an encrypted member
 * is unrelated to whether the archive can safely be stored unchanged.
 */
final class TransferZipStructure
{
    private static final long UINT_MAX = 0xffffffffL;

    private TransferZipStructure()
    {
    }

    static long validate(Path file, int maximumEntries) throws IOException
    {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r"))
        {
            Directory directory = readDirectory(input);
            if (directory.entries() > maximumEntries)
            {
                throw new ServiceException("压缩包内部条目超过" + maximumEntries + "个，已拒绝上传");
            }
            long cursor = directory.offset();
            for (long index = 0; index < directory.entries(); index++)
            {
                ByteBuffer central = read(input, cursor, 46, directory.end());
                require(central.getInt(0) == 0x02014b50, "Invalid central directory entry signature");
                int nameLength = unsignedShort(central, 28);
                int extraLength = unsignedShort(central, 30);
                int commentLength = unsignedShort(central, 32);
                require(unsignedShort(central, 34) == 0, "Multi-volume ZIP is not a complete archive");
                ByteBuffer variable = read(input, cursor + 46, nameLength + extraLength + commentLength,
                    directory.end());
                byte[] name = new byte[nameLength];
                variable.get(name);
                byte[] extra = new byte[extraLength];
                variable.get(extra);
                long uncompressed = unsignedInt(central, 24);
                long compressed = unsignedInt(central, 20);
                long localOffset = unsignedInt(central, 42);
                if (uncompressed == UINT_MAX || compressed == UINT_MAX || localOffset == UINT_MAX)
                {
                    ByteBuffer zip64 = zip64Field(extra);
                    if (uncompressed == UINT_MAX)
                    {
                        readZip64Value(zip64);
                    }
                    if (compressed == UINT_MAX)
                    {
                        compressed = readZip64Value(zip64);
                    }
                    if (localOffset == UINT_MAX)
                    {
                        localOffset = readZip64Value(zip64);
                    }
                }
                ByteBuffer local = read(input, localOffset, 30, directory.offset());
                require(local.getInt(0) == 0x04034b50, "Invalid local file header signature");
                require((unsignedShort(local, 6) & 0x0809) == (unsignedShort(central, 8) & 0x0809)
                    && unsignedShort(local, 8) == unsignedShort(central, 10),
                    "Local and central file flags or compression methods differ");
                int localNameLength = unsignedShort(local, 26);
                int localExtraLength = unsignedShort(local, 28);
                ByteBuffer localName = read(input, localOffset + 30, localNameLength, directory.offset());
                require(Arrays.equals(name, localName.array()), "Local and central file names differ");
                long dataOffset = localOffset + 30L + localNameLength + localExtraLength;
                require(dataOffset <= directory.offset() && compressed <= directory.offset() - dataOffset,
                    "ZIP member exceeds available archive data");
                cursor += 46L + nameLength + extraLength + commentLength;
            }
            // A central-directory digital signature is optional and does not
            // belong to the member count (APPNOTE 4.3.13).
            if (cursor != directory.end())
            {
                ByteBuffer signature = read(input, cursor, 6, directory.end());
                require(signature.getInt(0) == 0x05054b50
                    && cursor + 6L + unsignedShort(signature, 4) == directory.end(),
                    "Central directory length or member count is inconsistent");
            }
            return directory.entries();
        }
    }

    private static Directory readDirectory(RandomAccessFile input) throws IOException
    {
        long size = input.length();
        require(size >= 22, "Missing ZIP end record");
        int tailLength = (int) Math.min(size, 22L + 65535L);
        ByteBuffer tail = read(input, size - tailLength, tailLength, size);
        int endIndex = -1;
        for (int index = tailLength - 22; index >= 0; index--)
        {
            if (tail.getInt(index) == 0x06054b50
                && index + 22 + unsignedShort(tail, index + 20) == tailLength)
            {
                endIndex = index;
                break;
            }
        }
        require(endIndex >= 0, "Missing or truncated ZIP end record");
        require(unsignedShort(tail, endIndex + 4) == 0 && unsignedShort(tail, endIndex + 6) == 0,
            "Multi-volume ZIP is not a complete archive");
        long entries = unsignedShort(tail, endIndex + 10);
        require(unsignedShort(tail, endIndex + 8) == entries, "ZIP member counts differ");
        long directorySize = unsignedInt(tail, endIndex + 12);
        long directoryOffset = unsignedInt(tail, endIndex + 16);
        long directoryEnd = size - tailLength + endIndex;
        boolean hasZip64 = directoryEnd >= 20
            && read(input, directoryEnd - 20, 4, size).getInt(0) == 0x07064b50;
        if (hasZip64)
        {
            ByteBuffer locator = read(input, directoryEnd - 20, 20, size);
            require(unsignedInt(locator, 4) == 0 && unsignedInt(locator, 16) == 1,
                "Multi-volume ZIP64 is not a complete archive");
            long zip64Offset = locator.getLong(8);
            ByteBuffer zip64 = read(input, zip64Offset, 56, directoryEnd - 20);
            long recordSize = zip64.getLong(4);
            require(zip64.getInt(0) == 0x06064b50 && recordSize >= 44
                && recordSize == directoryEnd - 20 - zip64Offset - 12, "Invalid ZIP64 end record");
            require(unsignedInt(zip64, 16) == 0 && unsignedInt(zip64, 20) == 0,
                "Multi-volume ZIP64 is not a complete archive");
            long zip64Entries = zip64.getLong(32);
            require(zip64Entries >= 0 && zip64.getLong(24) == zip64Entries,
                "Invalid ZIP64 member count");
            require(entries == 65535 || entries == zip64Entries, "ZIP and ZIP64 member counts differ");
            entries = zip64Entries;
            directorySize = zip64.getLong(40);
            directoryOffset = zip64.getLong(48);
            directoryEnd = zip64Offset;
        }
        else
        {
            require(entries != 65535 && directorySize != UINT_MAX && directoryOffset != UINT_MAX,
                "Missing ZIP64 end record");
        }
        require(directoryOffset >= 0 && directorySize >= 0 && directoryOffset <= directoryEnd
            && directorySize == directoryEnd - directoryOffset
            && entries <= directorySize / 46, "Invalid central directory bounds");
        return new Directory(entries, directoryOffset, directoryEnd);
    }

    private static ByteBuffer zip64Field(byte[] extra) throws ZipException
    {
        ByteBuffer fields = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);
        while (fields.remaining() >= 4)
        {
            int tag = Short.toUnsignedInt(fields.getShort());
            int length = Short.toUnsignedInt(fields.getShort());
            require(length <= fields.remaining(), "Truncated ZIP extra field");
            if (tag == 1)
            {
                return fields.slice().order(ByteOrder.LITTLE_ENDIAN).limit(length);
            }
            fields.position(fields.position() + length);
        }
        throw new ZipException("Missing ZIP64 member metadata");
    }

    private static long readZip64Value(ByteBuffer value) throws ZipException
    {
        require(value.remaining() >= 8, "Truncated ZIP64 member metadata");
        long result = value.getLong();
        require(result >= 0, "Invalid ZIP64 member metadata");
        return result;
    }

    private static ByteBuffer read(RandomAccessFile input, long offset, int length, long limit) throws IOException
    {
        require(offset >= 0 && length >= 0 && offset <= limit && length <= limit - offset,
            "ZIP metadata exceeds available archive data");
        byte[] bytes = new byte[length];
        input.seek(offset);
        input.readFully(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int unsignedShort(ByteBuffer data, int offset)
    {
        return Short.toUnsignedInt(data.getShort(offset));
    }

    private static long unsignedInt(ByteBuffer data, int offset)
    {
        return Integer.toUnsignedLong(data.getInt(offset));
    }

    private static void require(boolean valid, String reason) throws ZipException
    {
        if (!valid)
        {
            throw new ZipException(reason);
        }
    }

    private record Directory(long entries, long offset, long end)
    {
    }
}
