package app.jer.axon.service;

public class StatusService {
    public static String status() {
        String playerStatus = PlayerService.getTextStatus();
        String baritoneStatus = BaritoneService.getTextStatus();

        return String.format(
                """
                        %s
                        %s""",
                playerStatus,
                baritoneStatus
        );
    }
}
