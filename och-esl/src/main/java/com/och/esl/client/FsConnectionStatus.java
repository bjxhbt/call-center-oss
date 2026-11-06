package com.och.esl.client;

/**
 * Snapshot of the two FreeSWITCH ESL capabilities used by the application.
 *
 * <p>The inbound ESL library exposes command writability and event delivery through
 * different asynchronous paths.  A recent event is useful evidence that the event
 * subscription remains alive even when a transient client writability check cannot
 * be observed by a web request thread.</p>
 */
public record FsConnectionStatus(
        String commandAddress,
        String eventAddress,
        Long lastEventAtEpochMillis,
        Long eventAgeMillis,
        boolean eventChannelActive) {

    /** Whether an ESL client is currently available for synchronous API commands. */
    public boolean commandChannelReady() {
        return commandAddress != null && !commandAddress.isBlank();
    }

    /** Best known address, preferring the command-capable connection. */
    public String bestKnownAddress() {
        return commandChannelReady() ? commandAddress : eventAddress;
    }
}
