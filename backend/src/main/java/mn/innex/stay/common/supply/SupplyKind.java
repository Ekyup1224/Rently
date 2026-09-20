package mn.innex.stay.common.supply;

/**
 * The three things a guest can be shown and a host can publish.
 *
 * <p>Lives here rather than in one module because several of them need to name a
 * listing without caring which kind it is: photo fingerprints, review flags and
 * anything else that treats supply uniformly.
 */
public enum SupplyKind {
    PROPERTY,
    HOTEL,
    ROOM_TYPE
}
