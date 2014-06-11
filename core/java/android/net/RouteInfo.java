/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.UnknownHostException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;

import java.util.Collection;
import java.util.Objects;

/**
 * Represents a network route.
 * <p>
 * This is used both to describe static network configuration and live network
 * configuration information.
 *
 * A route contains three pieces of information:
 * <ul>
 * <li>a destination {@link LinkAddress} for directly-connected subnets.  If this is
 *     {@code null} it indicates a default route of the address family (IPv4 or IPv6)
 *     implied by the gateway IP address.
 * <li>a gateway {@link InetAddress} for default routes.  If this is {@code null} it
 *     indicates a directly-connected route.
 * <li>an interface (which may be unspecified).
 * </ul>
 * Either the destination or the gateway may be {@code null}, but not both.  If the
 * destination and gateway are both specified, they must be of the same address family
 * (IPv4 or IPv6).
 */
public class RouteInfo implements Parcelable {
    /**
     * The IP destination address for this route.
     */
    private final LinkAddress mDestination;

    /**
     * The gateway address for this route.
     */
    private final InetAddress mGateway;

    /**
     * The interface for this route.
     */
    private final String mInterface;

    private final boolean mIsDefault;
    private final boolean mIsHost;
    private final boolean mHasGateway;

    /**
     * Constructs a RouteInfo object.
     *
     * If destination is null, then gateway must be specified and the
     * constructed route is either the IPv4 default route <code>0.0.0.0</code>
     * if the gateway is an instance of {@link Inet4Address}, or the IPv6 default
     * route <code>::/0</code> if gateway is an instance of
     * {@link Inet6Address}.
     * <p>
     * destination and gateway may not both be null.
     *
     * @param destination the destination prefix
     * @param gateway the IP address to route packets through
     * @param iface the interface name to send packets on
     */
    public RouteInfo(LinkAddress destination, InetAddress gateway, String iface) {
        if (destination == null) {
            if (gateway != null) {
                if (gateway instanceof Inet4Address) {
                    destination = new LinkAddress(Inet4Address.ANY, 0);
                } else {
                    destination = new LinkAddress(Inet6Address.ANY, 0);
                }
            } else {
                // no destination, no gateway. invalid.
                throw new IllegalArgumentException("Invalid arguments passed in: " + gateway + "," +
                                                   destination);
            }
        }
        if (gateway == null) {
            if (destination.getAddress() instanceof Inet4Address) {
                gateway = Inet4Address.ANY;
            } else {
                gateway = Inet6Address.ANY;
            }
        }
        mHasGateway = (!gateway.isAnyLocalAddress());

        mDestination = new LinkAddress(NetworkUtils.getNetworkPart(destination.getAddress(),
                destination.getNetworkPrefixLength()), destination.getNetworkPrefixLength());
        if ((destination.getAddress() instanceof Inet4Address &&
                 (gateway instanceof Inet4Address == false)) ||
                (destination.getAddress() instanceof Inet6Address &&
                 (gateway instanceof Inet6Address == false))) {
            throw new IllegalArgumentException("address family mismatch in RouteInfo constructor");
        }
        mGateway = gateway;
        mInterface = iface;
        mIsDefault = isDefault();
        mIsHost = isHost();
    }

    /**
     * Constructs a {@code RouteInfo} object.
     *
     * If destination is null, then gateway must be specified and the
     * constructed route is either the IPv4 default route <code>0.0.0.0</code>
     * if the gateway is an instance of {@link Inet4Address}, or the IPv6 default
     * route <code>::/0</code> if gateway is an instance of {@link Inet6Address}.
     * <p>
     * Destination and gateway may not both be null.
     *
     * @param destination the destination address and prefix in a {@link LinkAddress}
     * @param gateway the {@link InetAddress} to route packets through
     */
    public RouteInfo(LinkAddress destination, InetAddress gateway) {
        this(destination, gateway, null);
    }

    /**
     * Constructs a default {@code RouteInfo} object.
     *
     * @param gateway the {@link InetAddress} to route packets through
     */
    public RouteInfo(InetAddress gateway) {
        this(null, gateway, null);
    }

    /**
     * Constructs a {@code RouteInfo} object representing a direct connected subnet.
     *
     * @param destination the {@link LinkAddress} describing the address and prefix
     *                    length of the subnet.
     */
    public RouteInfo(LinkAddress destination) {
        this(destination, null, null);
    }

    /**
     * @hide
     */
    public static RouteInfo makeHostRoute(InetAddress host, String iface) {
        return makeHostRoute(host, null, iface);
    }

    /**
     * @hide
     */
    public static RouteInfo makeHostRoute(InetAddress host, InetAddress gateway, String iface) {
        if (host == null) return null;

        if (host instanceof Inet4Address) {
            return new RouteInfo(new LinkAddress(host, 32), gateway, iface);
        } else {
            return new RouteInfo(new LinkAddress(host, 128), gateway, iface);
        }
    }

    private boolean isHost() {
        return (mDestination.getAddress() instanceof Inet4Address &&
                mDestination.getNetworkPrefixLength() == 32) ||
               (mDestination.getAddress() instanceof Inet6Address &&
                mDestination.getNetworkPrefixLength() == 128);
    }

    private boolean isDefault() {
        boolean val = false;
        if (mGateway != null) {
            if (mGateway instanceof Inet4Address) {
                val = (mDestination == null || mDestination.getNetworkPrefixLength() == 0);
            } else {
                val = (mDestination == null || mDestination.getNetworkPrefixLength() == 0);
            }
        }
        return val;
    }

    /**
     * Retrieves the destination address and prefix length in the form of a {@link LinkAddress}.
     *
     * @return {@link LinkAddress} specifying the destination.  This is never {@code null}.
     */
    public LinkAddress getDestination() {
        return mDestination;
    }

    /**
     * Retrieves the gateway or next hop {@link InetAddress} for this route.
     *
     * @return {@link InetAddress} specifying the gateway or next hop.  This may be
     &                             {@code null} for a directly-connected route."
     */
    public InetAddress getGateway() {
        return mGateway;
    }

    /**
     * Retrieves the interface used for this route if specified, else {@code null}.
     *
     * @return The name of the interface used for this route.
     */
    public String getInterface() {
        return mInterface;
    }

    /**
     * Indicates if this route is a default route (ie, has no destination specified).
     *
     * @return {@code true} if the destination has a prefix length of 0.
     */
    public boolean isDefaultRoute() {
        return mIsDefault;
    }

    /**
     * Indicates if this route is a host route (ie, matches only a single host address).
     *
     * @return {@code true} if the destination has a prefix length of 32/128 for v4/v6.
     * @hide
     */
    public boolean isHostRoute() {
        return mIsHost;
    }

    /**
     * Indicates if this route has a next hop ({@code true}) or is directly-connected
     * ({@code false}).
     *
     * @return {@code true} if a gateway is specified
     * @hide
     */
    public boolean hasGateway() {
        return mHasGateway;
    }

    /**
     * Determines whether the destination and prefix of this route includes the specified
     * address.
     *
     * @param destination A {@link InetAddress} to test to see if it would match this route.
     * @return {@code true} if the destination and prefix length cover the given address.
     */
    public boolean matches(InetAddress destination) {
        if (destination == null) return false;

        // match the route destination and destination with prefix length
        InetAddress dstNet = NetworkUtils.getNetworkPart(destination,
                mDestination.getNetworkPrefixLength());

        return mDestination.getAddress().equals(dstNet);
    }

    /**
     * Find the route from a Collection of routes that best matches a given address.
     * May return null if no routes are applicable.
     * @param routes a Collection of RouteInfos to chose from
     * @param dest the InetAddress your trying to get to
     * @return the RouteInfo from the Collection that best fits the given address
     *
     * @hide
     */
    public static RouteInfo selectBestRoute(Collection<RouteInfo> routes, InetAddress dest) {
        if ((routes == null) || (dest == null)) return null;

        RouteInfo bestRoute = null;
        // pick a longest prefix match under same address type
        for (RouteInfo route : routes) {
            if (NetworkUtils.addressTypeMatches(route.mDestination.getAddress(), dest)) {
                if ((bestRoute != null) &&
                        (bestRoute.mDestination.getNetworkPrefixLength() >=
                        route.mDestination.getNetworkPrefixLength())) {
                    continue;
                }
                if (route.matches(dest)) bestRoute = route;
            }
        }
        return bestRoute;
    }

    public String toString() {
        String val = "";
        if (mDestination != null) val = mDestination.toString();
        if (mGateway != null) val += " -> " + mGateway.getHostAddress();
        return val;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof RouteInfo)) return false;

        RouteInfo target = (RouteInfo) obj;

        return Objects.equals(mDestination, target.getDestinationLinkAddress()) &&
                Objects.equals(mGateway, target.getGateway()) &&
                Objects.equals(mInterface, target.getInterface());
    }

    public int hashCode() {
        return (mDestination == null ? 0 : mDestination.hashCode() * 41)
                + (mGateway == null ? 0 :mGateway.hashCode() * 47)
                + (mInterface == null ? 0 :mInterface.hashCode() * 67)
                + (mIsDefault ? 3 : 7);
    }

    /**
     * Implement the Parcelable interface
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * Implement the Parcelable interface
     * @hide
     */
    public void writeToParcel(Parcel dest, int flags) {
        if (mDestination == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeByteArray(mDestination.getAddress().getAddress());
            dest.writeInt(mDestination.getNetworkPrefixLength());
        }

        if (mGateway == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeByteArray(mGateway.getAddress());
        }

        dest.writeString(mInterface);
    }

    /**
     * Implement the Parcelable interface.
     * @hide
     */
    public static final Creator<RouteInfo> CREATOR =
        new Creator<RouteInfo>() {
        public RouteInfo createFromParcel(Parcel in) {
            InetAddress destAddr = null;
            int prefix = 0;
            InetAddress gateway = null;

            if (in.readByte() == 1) {
                byte[] addr = in.createByteArray();
                prefix = in.readInt();

                try {
                    destAddr = InetAddress.getByAddress(addr);
                } catch (UnknownHostException e) {}
            }

            if (in.readByte() == 1) {
                byte[] addr = in.createByteArray();

                try {
                    gateway = InetAddress.getByAddress(addr);
                } catch (UnknownHostException e) {}
            }

            String iface = in.readString();

            LinkAddress dest = null;

            if (destAddr != null) {
                dest = new LinkAddress(destAddr, prefix);
            }

            return new RouteInfo(dest, gateway, iface);
        }

        public RouteInfo[] newArray(int size) {
            return new RouteInfo[size];
        }
    };
}
