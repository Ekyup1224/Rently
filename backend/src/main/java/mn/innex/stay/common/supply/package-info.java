/**
 * Value types shared by every kind of supply on the platform: houses today,
 * hotels from Step 3, and whatever comes next.
 *
 * <p>These live here rather than in {@link mn.innex.stay.listing} because a hotel
 * is not a kind of house. Having the hotel module import a house's cancellation
 * policy would read as a mistake, and duplicating a six-state lifecycle enum in
 * two modules guarantees the two drift apart.
 *
 * <p>Only types with genuinely identical meaning across supply types belong here.
 * Anything that means something different to a hotel than to a ger stays in its
 * own module.
 */
package mn.innex.stay.common.supply;
