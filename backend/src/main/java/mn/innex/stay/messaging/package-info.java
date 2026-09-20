/**
 * Messages between a guest and their host, always about a specific stay.
 *
 * <p>Conversations hang off bookings rather than existing freely. That is a
 * safety decision as much as a product one: cold messaging is how a scammer
 * reaches someone before any money is at risk, and a thread with no stay behind
 * it gives a moderator nothing to read it against.
 */
package mn.innex.stay.messaging;
