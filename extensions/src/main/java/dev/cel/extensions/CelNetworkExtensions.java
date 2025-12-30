// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.extensions;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.errorprone.annotations.Immutable;
import com.google.net.base.CidrAddressBlock;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.OpaqueType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * CEL Extension for Network functions (IP and CIDR).
 *
 * <p>Provides functions for creating, inspecting, and manipulating IP addresses and CIDR blocks,
 * maintaining consistency with the CEL Go and C++ network extensions.
 */
@Immutable
public final class CelNetworkExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  // Opaque Type Definitions
  public static final CelType IP_TYPE = OpaqueType.create("net.IP");
  public static final CelType CIDR_TYPE = OpaqueType.create("net.CIDR");

  // Package-private constructor
  CelNetworkExtensions() {
    this.functions = ImmutableSet.copyOf(Function.values());
  }

  // Constructor for creating subsets
  CelNetworkExtensions(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
  }

  /** Wrapper for InetAddress to represent the net.IP opaque type in CEL. */
  @Immutable
  public static class IpAddress {
    private final InetAddress address;

    private IpAddress(InetAddress address) {
      this.address = address;
    }

    public static IpAddress create(String val) {
      InetAddress addr = parseStrictIp(val);
      return new IpAddress(addr);
    }

    public static IpAddress create(InetAddress addr) {
      return new IpAddress(addr);
    }

    public InetAddress getAddress() {
      return address;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof IpAddress ipAddress)) {
        return false;
      }
      return address.equals(ipAddress.address);
    }

    @Override
    public int hashCode() {
      return address.hashCode();
    }

    @Override
    public String toString() {
      return InetAddresses.toAddrString(address);
    }
  }

  /** Wrapper for CidrAddressBlock to represent the net.CIDR opaque type in CEL. */
  @Immutable
  public static class CidrAddress {
    private final CidrAddressBlock block;
    private final InetAddress originalHost; // To preserve the non-truncated IP
    private final int prefixLength;

    private CidrAddress(CidrAddressBlock block, InetAddress originalHost, int prefixLength) {
      this.block = block;
      this.originalHost = originalHost;
      this.prefixLength = prefixLength;
    }

    public static CidrAddress create(String val) {
      String[] parts = val.split("/", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid CIDR string format: " + val);
      }
      InetAddress host = parseStrictIp(parts[0]);
      int prefixLength;
      try {
        prefixLength = Integer.parseInt(parts[1]);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid prefix length: " + parts[1], e);
      }

      if ((host instanceof Inet4Address && (prefixLength < 0 || prefixLength > 32))
          || (host instanceof Inet6Address && (prefixLength < 0 || prefixLength > 128))) {
        throw new IllegalArgumentException("Invalid prefix length for IP type: " + prefixLength);
      }

      return new CidrAddress(CidrAddressBlock.create(host, prefixLength), host, prefixLength);
    }

    public CidrAddressBlock getBlock() {
      return block;
    }

    public InetAddress getOriginalHost() {
      return originalHost;
    }

    public int getPrefixLength() {
      return prefixLength;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof CidrAddress that)) {
        return false;
      }
      return prefixLength == that.prefixLength && originalHost.equals(that.originalHost);
    }

    @Override
    public int hashCode() {
      return Objects.hash(originalHost, prefixLength);
    }

    @Override
    public String toString() {
      // Use InetAddresses.toAddrString to ensure canonical IPv6 formatting
      return InetAddresses.toAddrString(originalHost) + "/" + prefixLength;
    }
  }

  // --------------------------------------------------------------------------
  // Strict Parsing Helpers
  // --------------------------------------------------------------------------
  private static InetAddress parseStrictIp(String val) {
    if (val == null || val.isEmpty()) {
      throw new IllegalArgumentException("IP address string cannot be null or empty");
    }
    if (val.contains("%")) {
      throw new IllegalArgumentException("IP address string must not include a zone index: " + val);
    }
    String ipStr = val;
    if (ipStr.startsWith("[") && ipStr.endsWith("]")) { // Pure IPv6 in brackets
      ipStr = ipStr.substring(1, ipStr.length() - 1);
    } else if (ipStr.contains(":") && ipStr.lastIndexOf(':') != ipStr.indexOf(':')) { // IPv6
      // Handled by InetAddresses.forString
    } else if (ipStr.contains(":")) { // Potentially IPv4 with port or invalid
      throw new IllegalArgumentException("Invalid IP address format: " + val);
    }

    InetAddress addr;
    try {
      addr = InetAddresses.forString(ipStr);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid IP address string: " + val, e);
    }

    if (addr instanceof Inet6Address) {
      if (InetAddresses.isMappedIPv4Address(addr.getHostAddress())) {
        throw new IllegalArgumentException("IPv4-mapped IPv6 addresses are not allowed: " + val);
      }
    }
    return addr;
  }

  // --------------------------------------------------------------------------
  // Function Enum & Declarations
  // --------------------------------------------------------------------------
  /** Enum of all functions in this extension. */
  public enum Function {
    IS_IP(
        CelFunctionDecl.newFunctionDeclaration(
            "isIP",
            CelOverloadDecl.newGlobalOverload(
                "is_ip_string",
                "Checks if a string is a valid IP address",
                SimpleType.BOOL,
                ImmutableList.of(SimpleType.STRING))),
        CelFunctionBinding.from("is_ip_string", String.class, CelNetworkExtensions::isIp)),
    STRING_TO_IP(
        CelFunctionDecl.newFunctionDeclaration(
            "ip",
            CelOverloadDecl.newGlobalOverload(
                "string_to_ip",
                "Converts a string to an IP address object",
                IP_TYPE,
                ImmutableList.of(SimpleType.STRING))),
        CelFunctionBinding.from("string_to_ip", String.class, CelNetworkExtensions::stringToIp)),
    IS_CIDR(
        CelFunctionDecl.newFunctionDeclaration(
            "isCIDR",
            CelOverloadDecl.newGlobalOverload(
                "is_cidr_string",
                "Checks if a string is a valid CIDR notation",
                SimpleType.BOOL,
                ImmutableList.of(SimpleType.STRING))),
        CelFunctionBinding.from("is_cidr_string", String.class, CelNetworkExtensions::isCidr)),
    STRING_TO_CIDR(
        CelFunctionDecl.newFunctionDeclaration(
            "cidr",
            CelOverloadDecl.newGlobalOverload(
                "string_to_cidr",
                "Converts a string to a CIDR object",
                CIDR_TYPE,
                ImmutableList.of(SimpleType.STRING))),
        CelFunctionBinding.from(
            "string_to_cidr", String.class, CelNetworkExtensions::stringToCidr)),
    IP_IS_CANONICAL(
        CelFunctionDecl.newFunctionDeclaration(
            "isCanonical",
            CelOverloadDecl.newMemberOverload(
                "ip_is_canonical_string",
                "Checks if a string is a canonical representation of an IP address",
                SimpleType.BOOL,
                ImmutableList.of(SimpleType.STRING)),
            CelOverloadDecl.newMemberOverload(
                "ip_is_canonical_ip",
                "Checks if an IP address object is a canonical representation",
                SimpleType.BOOL,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from(
            "ip_is_canonical_string", String.class, CelNetworkExtensions::ipIsCanonicalString),
        CelFunctionBinding.from(
            "ip_is_canonical_ip", IpAddress.class, CelNetworkExtensions::ipIsCanonical)),
    IP_FAMILY(
        CelFunctionDecl.newFunctionDeclaration(
            "family",
            CelOverloadDecl.newMemberOverload(
                "ip_family",
                "Returns the IP family (4 or 6)",
                SimpleType.INT,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from("ip_family", IpAddress.class, CelNetworkExtensions::ipFamily)),
    IP_IS_LOOPBACK(
        CelFunctionDecl.newFunctionDeclaration(
            "isLoopback",
            CelOverloadDecl.newMemberOverload(
                "ip_is_loopback",
                "Checks if the IP is a loopback address",
                SimpleType.BOOL,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from(
            "ip_is_loopback", IpAddress.class, CelNetworkExtensions::ipIsLoopback)),
    IP_IS_GLOBAL_UNICAST(
        CelFunctionDecl.newFunctionDeclaration(
            "isGlobalUnicast",
            CelOverloadDecl.newMemberOverload(
                "ip_is_global_unicast",
                "Checks if the IP is a global unicast address",
                SimpleType.BOOL,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from(
            "ip_is_global_unicast", IpAddress.class, CelNetworkExtensions::ipIsGlobalUnicast)),
    IP_IS_LINK_LOCAL_MULTICAST(
        CelFunctionDecl.newFunctionDeclaration(
            "isLinkLocalMulticast",
            CelOverloadDecl.newMemberOverload(
                "ip_is_link_local_multicast",
                "Checks if the IP is a link-local multicast address",
                SimpleType.BOOL,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from(
            "ip_is_link_local_multicast",
            IpAddress.class,
            CelNetworkExtensions::ipIsLinkLocalMulticast)),
    IP_IS_LINK_LOCAL_UNICAST(
        CelFunctionDecl.newFunctionDeclaration(
            "isLinkLocalUnicast",
            CelOverloadDecl.newMemberOverload(
                "ip_is_link_local_unicast",
                "Checks if the IP is a link-local unicast address",
                SimpleType.BOOL,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from(
            "ip_is_link_local_unicast",
            IpAddress.class,
            CelNetworkExtensions::ipIsLinkLocalUnicast)),
    IP_IS_UNSPECIFIED(
        CelFunctionDecl.newFunctionDeclaration(
            "isUnspecified",
            CelOverloadDecl.newMemberOverload(
                "ip_is_unspecified",
                "Checks if the IP is an unspecified address",
                SimpleType.BOOL,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from(
            "ip_is_unspecified", IpAddress.class, CelNetworkExtensions::ipIsUnspecified)),
    IP_TO_STRING(
        CelFunctionDecl.newFunctionDeclaration(
            "string",
            CelOverloadDecl.newMemberOverload(
                "ip_to_string",
                "Converts the IP address to its string representation",
                SimpleType.STRING,
                ImmutableList.of(IP_TYPE))),
        CelFunctionBinding.from("ip_to_string", IpAddress.class, IpAddress::toString)),
    CIDR_IP(
        CelFunctionDecl.newFunctionDeclaration(
            "ip",
            CelOverloadDecl.newMemberOverload(
                "cidr_ip",
                "Returns the base IP address of the CIDR block",
                IP_TYPE,
                ImmutableList.of(CIDR_TYPE))),
        CelFunctionBinding.from("cidr_ip", CidrAddress.class, CelNetworkExtensions::cidrIp)),
    CIDR_CONTAINS_IP(
        CelFunctionDecl.newFunctionDeclaration(
            "containsIP",
            CelOverloadDecl.newMemberOverload(
                "cidr_contains_ip_ip",
                "Checks if the CIDR block contains the given IP address object",
                SimpleType.BOOL,
                ImmutableList.of(CIDR_TYPE, IP_TYPE)),
            CelOverloadDecl.newMemberOverload(
                "cidr_contains_ip_string",
                "Checks if the CIDR block contains the given IP address string",
                SimpleType.BOOL,
                ImmutableList.of(CIDR_TYPE, SimpleType.STRING))),
        CelFunctionBinding.from(
            "cidr_contains_ip_ip",
            CidrAddress.class,
            IpAddress.class,
            CelNetworkExtensions::cidrContainsIp),
        CelFunctionBinding.from(
            "cidr_contains_ip_string",
            CidrAddress.class,
            String.class,
            CelNetworkExtensions::cidrContainsIpString)),
    CIDR_CONTAINS_CIDR(
        CelFunctionDecl.newFunctionDeclaration(
            "containsCIDR",
            CelOverloadDecl.newMemberOverload(
                "cidr_contains_cidr_cidr",
                "Checks if the CIDR block contains the other CIDR block object",
                SimpleType.BOOL,
                ImmutableList.of(CIDR_TYPE, CIDR_TYPE)),
            CelOverloadDecl.newMemberOverload(
                "cidr_contains_cidr_string",
                "Checks if the CIDR block contains the other CIDR block string",
                SimpleType.BOOL,
                ImmutableList.of(CIDR_TYPE, SimpleType.STRING))),
        CelFunctionBinding.from(
            "cidr_contains_cidr_cidr",
            CidrAddress.class,
            CidrAddress.class,
            CelNetworkExtensions::cidrContainsCidr),
        CelFunctionBinding.from(
            "cidr_contains_cidr_string",
            CidrAddress.class,
            String.class,
            CelNetworkExtensions::cidrContainsCidrString)),
    CIDR_MASKED(
        CelFunctionDecl.newFunctionDeclaration(
            "masked",
            CelOverloadDecl.newMemberOverload(
                "cidr_masked",
                "Returns the network address (masked IP) of the CIDR block",
                CIDR_TYPE,
                ImmutableList.of(CIDR_TYPE))),
        CelFunctionBinding.from(
            "cidr_masked", CidrAddress.class, CelNetworkExtensions::cidrMasked)),
    CIDR_PREFIX_LENGTH(
        CelFunctionDecl.newFunctionDeclaration(
            "prefixLength",
            CelOverloadDecl.newMemberOverload(
                "cidr_prefix_length",
                "Returns the prefix length of the CIDR block",
                SimpleType.INT,
                ImmutableList.of(CIDR_TYPE))),
        CelFunctionBinding.from(
            "cidr_prefix_length", CidrAddress.class, CelNetworkExtensions::cidrPrefixLength)),
    CIDR_TO_STRING(
        CelFunctionDecl.newFunctionDeclaration(
            "string",
            CelOverloadDecl.newMemberOverload(
                "cidr_to_string",
                "Converts the CIDR block to its string representation",
                SimpleType.STRING,
                ImmutableList.of(CIDR_TYPE))),
        CelFunctionBinding.from("cidr_to_string", CidrAddress.class, CidrAddress::toString));

    private final CelFunctionDecl functionDecl;
    private final ImmutableSet<CelFunctionBinding> bindings;

    public String getFunction() {
      return functionDecl.name();
    }

    Function(CelFunctionDecl functionDecl, CelFunctionBinding... bindings) {
      this.functionDecl = functionDecl;
      this.bindings = ImmutableSet.copyOf(bindings);
    }

    public CelFunctionDecl getFunctionDecl() {
      return functionDecl;
    }

    public ImmutableSet<CelFunctionBinding> getBindings() {
      return bindings;
    }
  }

  private final ImmutableSet<Function> functions;

  // --------------------------------------------------------------------------
  // Library Registration
  // --------------------------------------------------------------------------
  private static final CelExtensionLibrary<CelNetworkExtensions> LIBRARY =
      new CelExtensionLibrary<CelNetworkExtensions>() {
        private final CelNetworkExtensions version0 = new CelNetworkExtensions();

        @Override
        public String name() {
          return "network";
        }

        @Override
        public ImmutableSet<CelNetworkExtensions> versions() {
          return ImmutableSet.of(version0);
        }
      };

  public static CelExtensionLibrary<CelNetworkExtensions> library() {
    return LIBRARY;
  }

  @Override
  public int version() {
    return 0;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions.stream().map(Function::getFunctionDecl).collect(ImmutableSet.toImmutableSet());
  }

  // --------------------------------------------------------------------------
  // CelCheckerLibrary Implementation
  // --------------------------------------------------------------------------
  @Override
  public void setCheckerOptions(CelCheckerBuilder builder) {
    for (Function func : functions) {
      builder.addFunctionDeclarations(func.getFunctionDecl());
    }
    builder.setTypeProvider(new NetworkTypeProvider());
  }

  // --------------------------------------------------------------------------
  // CelRuntimeLibrary Implementation
  // --------------------------------------------------------------------------
  @Override
  public void setRuntimeOptions(CelRuntimeBuilder builder) {
    for (Function func : functions) {
      builder.addFunctionBindings(func.getBindings());
    }
  }

  // --------------------------------------------------------------------------
  // Function Implementations
  // --------------------------------------------------------------------------
  private static boolean isIp(String val) {
    try {
      parseStrictIp(val);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static IpAddress stringToIp(String val) {
    return IpAddress.create(val);
  }

  private static boolean isCidr(String val) {
    try {
      CidrAddress.create(val);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static CidrAddress stringToCidr(String val) {
    return CidrAddress.create(val);
  }

  private static boolean ipIsCanonicalString(String val) {
    return ipIsCanonical(IpAddress.create(val));
  }

  private static boolean ipIsCanonical(IpAddress ip) {
    InetAddress addr = ip.getAddress();
    // InetAddresses.toAddrString() returns the canonical string form.
    // We check if the input string matches this canonical form.
    return InetAddresses.toAddrString(addr).equals(ip.toString());
  }

  private static long ipFamily(IpAddress ip) {
    return (ip.getAddress() instanceof Inet4Address) ? 4L : 6L;
  }

  private static boolean ipIsLoopback(IpAddress ip) {
    return ip.getAddress().isLoopbackAddress();
  }

  private static boolean ipIsGlobalUnicast(IpAddress ip) {
    InetAddress addr = ip.getAddress();
    return !addr.isAnyLocalAddress()
        && !addr.isLoopbackAddress()
        && !addr.isLinkLocalAddress()
        && !addr.isSiteLocalAddress()
        && !addr.isMulticastAddress();
  }

  private static boolean ipIsLinkLocalMulticast(IpAddress ip) {
    return ip.getAddress().isMCLinkLocal();
  }

  private static boolean ipIsLinkLocalUnicast(IpAddress ip) {
    return ip.getAddress().isLinkLocalAddress() && !ip.getAddress().isMulticastAddress();
  }

  private static boolean ipIsUnspecified(IpAddress ip) {
    return ip.getAddress().isAnyLocalAddress();
  }

  private static IpAddress cidrIp(CidrAddress cidr) {
    return IpAddress.create(cidr.getOriginalHost());
  }

  private static boolean cidrContainsIp(CidrAddress cidr, IpAddress ip) {
    return cidr.getBlock().contains(ip.getAddress());
  }

  private static boolean cidrContainsIpString(CidrAddress cidr, String ipStr) {
    try {
      return cidr.getBlock().contains(parseStrictIp(ipStr));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid IP string in containsIP", e);
    }
  }

  private static boolean cidrContainsCidr(CidrAddress parent, CidrAddress child) {
    return parent.getBlock().contains(child.getBlock());
  }

  private static boolean cidrContainsCidrString(CidrAddress parent, String childStr) {
    try {
      CidrAddress child = CidrAddress.create(childStr);
      return parent.getBlock().contains(child.getBlock());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid CIDR string in containsCIDR", e);
    }
  }

  private static CidrAddress cidrMasked(CidrAddress cidr) {
    CidrAddressBlock maskedBlock = cidr.getBlock();
    return new CidrAddress(maskedBlock, maskedBlock.getInetAddress(), cidr.getPrefixLength());
  }

  private static long cidrPrefixLength(CidrAddress cidr) {
    return (long) cidr.getPrefixLength();
  }

  // --------------------------------------------------------------------------
  // Custom Type Provider
  // --------------------------------------------------------------------------
  @Immutable
  private static class NetworkTypeProvider implements CelTypeProvider {
    private static final ImmutableSet<CelType> SUPPORTED_TYPES =
        ImmutableSet.of(IP_TYPE, CIDR_TYPE);

    @Override
    public Optional<CelType> findType(String typeName) {
      if (typeName.equals(IP_TYPE.name())) {
        return Optional.of(IP_TYPE);
      }
      if (typeName.equals(CIDR_TYPE.name())) {
        return Optional.of(CIDR_TYPE);
      }
      return Optional.empty();
    }

    @Override
    public ImmutableCollection<CelType> types() {
      return SUPPORTED_TYPES;
    }
  }
}
