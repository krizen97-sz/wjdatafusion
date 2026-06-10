package com.hm.manage.domain.vo;

public class WhitelistKafkaPullResultVo
{
    private int polledMessages;
    private int parsedMessages;
    private int insertedRows;
    private int skippedMessages;

    public int getPolledMessages()
    {
        return polledMessages;
    }

    public void setPolledMessages(int polledMessages)
    {
        this.polledMessages = polledMessages;
    }

    public int getParsedMessages()
    {
        return parsedMessages;
    }

    public void setParsedMessages(int parsedMessages)
    {
        this.parsedMessages = parsedMessages;
    }

    public int getInsertedRows()
    {
        return insertedRows;
    }

    public void setInsertedRows(int insertedRows)
    {
        this.insertedRows = insertedRows;
    }

    public int getSkippedMessages()
    {
        return skippedMessages;
    }

    public void setSkippedMessages(int skippedMessages)
    {
        this.skippedMessages = skippedMessages;
    }
}
