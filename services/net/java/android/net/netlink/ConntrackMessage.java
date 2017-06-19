/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net.netlink;

import static android.net.netlink.StructNlAttr.NLA_HEADERLEN;
import static android.net.netlink.StructNlMsgHdr.NLM_F_ACK;
import static android.net.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static android.net.netlink.StructNlMsgHdr.NLM_F_REPLACE;
import static android.net.netlink.StructNlMsgHdr.NLM_F_REQUEST;
import static android.net.util.NetworkConstants.IPV4_ADDR_LEN;

import android.net.netlink.StructNdMsg;
import android.net.netlink.StructNlAttr;
import android.net.netlink.StructNlMsgHdr;
import android.net.netlink.NetlinkMessage;
import android.system.OsConstants;
import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * A NetlinkMessage subclass for netlink conntrack messages.
 *
 * see also: &lt;linux_src&gt;/include/uapi/linux/netfilter/nfnetlink_conntrack.h
 *
 * @hide
 */
public class ConntrackMessage extends NetlinkMessage {
    public static final short NFNL_SUBSYS_CTNETLINK = 1;
    public static final short IPCTNL_MSG_CT_NEW = 0;

    // enum ctattr_type
    public static final short CTA_TUPLE_ORIG  = 1;
    public static final short CTA_TUPLE_REPLY = 2;
    public static final short CTA_TIMEOUT     = 7;

    // enum ctattr_tuple
    public static final short CTA_TUPLE_IP    = 1;
    public static final short CTA_TUPLE_PROTO = 2;

    // enum ctattr_ip
    public static final short CTA_IP_V4_SRC = 1;
    public static final short CTA_IP_V4_DST = 2;

    // enum ctattr_l4proto
    public static final short CTA_PROTO_NUM      = 1;
    public static final short CTA_PROTO_SRC_PORT = 2;
    public static final short CTA_PROTO_DST_PORT = 3;

    public static byte[] newIPv4UpdateRequest(
            int proto, Inet4Address src, int sport, Inet4Address dst, int dport) {
        final int NFGEN_MSG_SIZE = 4;
        final int CT_NEW_SIZE = NFGEN_MSG_SIZE + 60;
        final int length = StructNlMsgHdr.STRUCT_SIZE + CT_NEW_SIZE;
        final byte[] bytes = new byte[length];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.nativeOrder());

        final StructNlMsgHdr nlmsghdr = new StructNlMsgHdr();
        nlmsghdr.nlmsg_len = length;
        nlmsghdr.nlmsg_type = (NFNL_SUBSYS_CTNETLINK << 8) | IPCTNL_MSG_CT_NEW;
        nlmsghdr.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
        nlmsghdr.nlmsg_seq = 1;
        nlmsghdr.pack(byteBuffer);

        // TODO: Consider making a separate StructNfGenMsg and using it here.
        byteBuffer.put((byte) OsConstants.AF_INET);
        final byte NFNETLINK_V0 = (byte) 0;
        byteBuffer.put(NFNETLINK_V0);
        byteBuffer.putShort((short) 0);

        // StructNlAttr(len=4+20+28, attr=CTA_TUPLE_ORIG)
        byteBuffer.putShort((short) (NLA_HEADERLEN + 20 + 28));
        byteBuffer.putShort(nested(CTA_TUPLE_ORIG));
        {
            // StructNlAttr(len=4+8+8=20, attr=CTA_TUPLE_IP)
            byteBuffer.putShort((short) (NLA_HEADERLEN + 2*8));
            byteBuffer.putShort(nested(CTA_TUPLE_IP));
            {
                // StructNlAttr(len=8, attr=CTA_IP_V4_SRC, value=addr_bytes)
                final StructNlAttr src4 = new StructNlAttr();
                src4.nla_len = (short) (NLA_HEADERLEN + IPV4_ADDR_LEN);
                src4.nla_type = CTA_IP_V4_SRC;
                src4.nla_value = src.getAddress();
                src4.pack(byteBuffer);

                // StructNlAttr(len=8, attr=CTA_IP_V4_DST, value=addr_bytes)
                final StructNlAttr dst4 = new StructNlAttr();
                dst4.nla_len = (short) (NLA_HEADERLEN + IPV4_ADDR_LEN);
                dst4.nla_type = CTA_IP_V4_DST;
                dst4.nla_value = dst.getAddress();
                dst4.pack(byteBuffer);
            }

            // StructNlAttr(len=4+3*8=28, attr=CTA_TUPLE_PROTO)
            byteBuffer.putShort((short) (NLA_HEADERLEN + 3*8));
            byteBuffer.putShort(nested(CTA_TUPLE_PROTO));
            {
                StructNlAttr.pack(byteBuffer, CTA_PROTO_NUM, (byte) proto);
                StructNlAttr.pack(byteBuffer, CTA_PROTO_SRC_PORT, (short) sport);
                StructNlAttr.pack(byteBuffer, CTA_PROTO_DST_PORT, (short) dport);
            }
        }

        // StructNlAttr(len=4+4, attr=CTA_TIMEOUT, value=timestamp)
        byteBuffer.putShort((short) (NLA_HEADERLEN + 4));
        byteBuffer.putShort(CTA_TIMEOUT);
        byteBuffer.putInt(30);

        return bytes;
    }

    private ConntrackMessage(StructNlMsgHdr header) {
        super(header);
    }

    private static short nested(short type) {
        final int NLA_F_NESTED = (1 << 15);
        return (short) (((int) type) | NLA_F_NESTED);
    }
}
