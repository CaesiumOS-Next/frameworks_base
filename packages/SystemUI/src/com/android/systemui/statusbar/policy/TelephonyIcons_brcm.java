/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import com.android.systemui.R;

// BRCM DUAL SIM
// This is modified from TelephonyIcons.java for dual SIM icons
class TelephonyIcons_brcm {
    //***** Signal strength icons

    //GSM/UMTS
    static final int[][][] TELEPHONY_SIGNAL_STRENGTH = {
        { // SIM1
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
        },
        { // SIM2
        { R.drawable.stat_sys_signal_0_fully_1,
          R.drawable.stat_sys_signal_1_fully_1,
          R.drawable.stat_sys_signal_2_fully_1,
          R.drawable.stat_sys_signal_3_fully_1,
          R.drawable.stat_sys_signal_4_fully_1 },
        { R.drawable.stat_sys_signal_0_fully_1,
          R.drawable.stat_sys_signal_1_fully_1,
          R.drawable.stat_sys_signal_2_fully_1,
          R.drawable.stat_sys_signal_3_fully_1,
          R.drawable.stat_sys_signal_4_fully_1 }
        }
    };

    static final int[][][] QS_TELEPHONY_SIGNAL_STRENGTH = {
        { // SIM1
        { R.drawable.ic_qs_signal_0,
          R.drawable.ic_qs_signal_1,
          R.drawable.ic_qs_signal_2,
          R.drawable.ic_qs_signal_3,
          R.drawable.ic_qs_signal_4 },
        { R.drawable.ic_qs_signal_full_0,
          R.drawable.ic_qs_signal_full_1,
          R.drawable.ic_qs_signal_full_2,
          R.drawable.ic_qs_signal_full_3,
          R.drawable.ic_qs_signal_full_4 }
        },
        { // SIM2
        { R.drawable.ic_qs_signal_0_1,
          R.drawable.ic_qs_signal_1_1,
          R.drawable.ic_qs_signal_2_1,
          R.drawable.ic_qs_signal_3_1,
          R.drawable.ic_qs_signal_4_1 },
        { R.drawable.ic_qs_signal_full_0_1,
          R.drawable.ic_qs_signal_full_1_1,
          R.drawable.ic_qs_signal_full_2_1,
          R.drawable.ic_qs_signal_full_3_1,
          R.drawable.ic_qs_signal_full_4_1 }
        }
    };

    static final int[][][] TELEPHONY_SIGNAL_STRENGTH_ROAMING = {
        { // SIM1
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
        },
        { // SIM2
        { R.drawable.stat_sys_signal_0_fully_1,
          R.drawable.stat_sys_signal_1_fully_1,
          R.drawable.stat_sys_signal_2_fully_1,
          R.drawable.stat_sys_signal_3_fully_1,
          R.drawable.stat_sys_signal_4_fully_1 },
        { R.drawable.stat_sys_signal_0_fully_1,
          R.drawable.stat_sys_signal_1_fully_1,
          R.drawable.stat_sys_signal_2_fully_1,
          R.drawable.stat_sys_signal_3_fully_1,
          R.drawable.stat_sys_signal_4_fully_1 }
        }
    };

    static final int[][] QS_DATA_R = {
        { // SIM1
        R.drawable.ic_qs_signal_r,
        R.drawable.ic_qs_signal_full_r
		},
        { // SIM2
        R.drawable.ic_qs_signal_r,
        R.drawable.ic_qs_signal_full_r
		}
    };

    static final int[][][] DATA_SIGNAL_STRENGTH = TELEPHONY_SIGNAL_STRENGTH;

    //***** Data connection icons

    //GSM/UMTS
    static final int[][][] DATA_G = {
            { // SIM1
            { R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1 },
            { R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1 }
            },
            { // SIM2
            { R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1 },
            { R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1,
              R.drawable.stat_sys_data_fully_connected_g_1 }
            }
        };

    static final int[][] QS_DATA_G = {
		{//SIM1
        R.drawable.ic_qs_signal_g,
        R.drawable.ic_qs_signal_full_g
		},
		{ //SIM2
        R.drawable.ic_qs_signal_g,
        R.drawable.ic_qs_signal_full_g
		}
    };

    static final int[][][] DATA_3G = {
            { // SIM1
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g }
            },
            { // SIM2
            { R.drawable.stat_sys_data_fully_connected_3g_1,
              R.drawable.stat_sys_data_fully_connected_3g_1,
              R.drawable.stat_sys_data_fully_connected_3g_1,
              R.drawable.stat_sys_data_fully_connected_3g_1 },
            { R.drawable.stat_sys_data_fully_connected_3g_1,
              R.drawable.stat_sys_data_fully_connected_3g_1,
              R.drawable.stat_sys_data_fully_connected_3g_1,
              R.drawable.stat_sys_data_fully_connected_3g_1 }
            }
        };

    static final int[][] QS_DATA_3G = {
		{//SIM1
        R.drawable.ic_qs_signal_3g,
        R.drawable.ic_qs_signal_full_3g
		},
		{//SIM2
        R.drawable.ic_qs_signal_3g,
        R.drawable.ic_qs_signal_full_3g
		}
    };

    static final int[][][] DATA_E = {
            { // SIM1
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e }
            },
            { // SIM2
            { R.drawable.stat_sys_data_fully_connected_e_1,
              R.drawable.stat_sys_data_fully_connected_e_1,
              R.drawable.stat_sys_data_fully_connected_e_1,
              R.drawable.stat_sys_data_fully_connected_e_1 },
            { R.drawable.stat_sys_data_fully_connected_e_1,
              R.drawable.stat_sys_data_fully_connected_e_1,
              R.drawable.stat_sys_data_fully_connected_e_1,
              R.drawable.stat_sys_data_fully_connected_e_1 }
            }
        };

    static final int[][] QS_DATA_E = {
		{//SIM1
        R.drawable.ic_qs_signal_e,
        R.drawable.ic_qs_signal_full_e
		},
		{//SIM2
        R.drawable.ic_qs_signal_e,
        R.drawable.ic_qs_signal_full_e
		}
    };
    //3.5G
    static final int[][][] DATA_H = {
            { // SIM1
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h }
            },
            { // SIM2
            { R.drawable.stat_sys_data_connected_h_1,
              R.drawable.stat_sys_data_connected_h_1,
              R.drawable.stat_sys_data_connected_h_1,
              R.drawable.stat_sys_data_connected_h_1 },
            { R.drawable.stat_sys_data_fully_connected_h_1,
              R.drawable.stat_sys_data_fully_connected_h_1,
              R.drawable.stat_sys_data_fully_connected_h_1,
              R.drawable.stat_sys_data_fully_connected_h_1 }
            }
    };


    static final int[][] QS_DATA_H = {
				{//SIM1
                R.drawable.ic_qs_signal_h,
                R.drawable.ic_qs_signal_full_h
				},
				{//SIM2
                R.drawable.ic_qs_signal_h,
                R.drawable.ic_qs_signal_full_h
			}
	};
    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][][] DATA_1X = {
            { // SIM1
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x }
            },
            { // SIM2
            { R.drawable.stat_sys_data_fully_connected_1x_1,
              R.drawable.stat_sys_data_fully_connected_1x_1,
              R.drawable.stat_sys_data_fully_connected_1x_1,
              R.drawable.stat_sys_data_fully_connected_1x_1 },
            { R.drawable.stat_sys_data_fully_connected_1x_1,
              R.drawable.stat_sys_data_fully_connected_1x_1,
              R.drawable.stat_sys_data_fully_connected_1x_1,
              R.drawable.stat_sys_data_fully_connected_1x_1 }
            }
            };

    static final int[][] QS_DATA_1X = {
		{//SIM1
        R.drawable.ic_qs_signal_1x,
        R.drawable.ic_qs_signal_full_1x
		},
		{//SIM2
        R.drawable.ic_qs_signal_1x,
        R.drawable.ic_qs_signal_full_1x
		}
    };
    // LTE and eHRPD
    static final int[][][] DATA_4G = {
            { // SIM1
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g },
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g }
            },
            { // SIM2
            { R.drawable.stat_sys_data_fully_connected_4g_1,
              R.drawable.stat_sys_data_fully_connected_4g_1,
              R.drawable.stat_sys_data_fully_connected_4g_1,
              R.drawable.stat_sys_data_fully_connected_4g_1 },
            { R.drawable.stat_sys_data_fully_connected_4g_1,
              R.drawable.stat_sys_data_fully_connected_4g_1,
              R.drawable.stat_sys_data_fully_connected_4g_1,
              R.drawable.stat_sys_data_fully_connected_4g_1 }
            }
        };

    static final int[][] QS_DATA_4G = {
		{//SIM1
        R.drawable.ic_qs_signal_4g,
        R.drawable.ic_qs_signal_full_4g
		},
		{//SIM2
        R.drawable.ic_qs_signal_4g,
        R.drawable.ic_qs_signal_full_4g
		}
    };

    // LTE branded "LTE"
    static final int[][][] DATA_LTE = {
			{//SIM1
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte },
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte }
			},
			{//SIM2
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte },
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte }
			}
    };

    static final int[][] QS_DATA_LTE = {
		{//SIM1
        R.drawable.ic_qs_signal_lte,
        R.drawable.ic_qs_signal_full_lte
		},
		{//SIM2
        R.drawable.ic_qs_signal_lte,
        R.drawable.ic_qs_signal_full_lte
		}
    };

}
