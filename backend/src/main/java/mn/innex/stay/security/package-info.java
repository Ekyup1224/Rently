/**
 * Authentication and authorization infrastructure: JWT issuing and decoding,
 * the Spring Security filter chain, and helpers for reading the current actor.
 *
 * <p>Identity <em>data</em> (users, roles, organizations) lives in
 * {@link mn.innex.stay.user}; this package only deals in tokens and grants.
 */
package mn.innex.stay.security;
