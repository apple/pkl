/**
 * <strong>Internal</strong> SPI for executing Pkl code with different Pkl distributions.
 *
 * <p>CAUTION: Every class X under `spi` MUST adhere to the following rules:
 *
 * <ol>
 *   <li>X MUST live in a versioned subpackage (v1, v2, etc.).
 *   <li>Any change made to X MUST be binary compatible.
 *   <li>X MUST only use classes from its own package and Java platform packages.
 * </ol>
 *
 * <p>NOTE: For backwards compatibility reasons, this package still contains the name
 * <strong>pie</strong>.
 */
package org.pkl.executor.spi;
