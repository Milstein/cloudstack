// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.storage;

import java.util.ArrayList;
import java.util.List;

public class Storage {
    public static enum ImageFormat {
        QCOW2(true, true, false, "qcow2"),
        RAW(false, false, false, "raw"),
        VHD(true, true, true, "vhd"),
        ISO(false, false, false, "iso"),
        OVA(true, true, true, "ova"),
        VHDX(true, true, true, "vhdx"),
        BAREMETAL(false, false, false, "BAREMETAL"),
        TAR(false, false, false, "tar");

        private final boolean thinProvisioned;
        private final boolean supportSparse;
        private final boolean supportSnapshot;
        private final String fileExtension;

        private ImageFormat(boolean thinProvisioned, boolean supportSparse, boolean supportSnapshot) {
            this.thinProvisioned = thinProvisioned;
            this.supportSparse = supportSparse;
            this.supportSnapshot = supportSnapshot;
            fileExtension = null;
        }

        private ImageFormat(boolean thinProvisioned, boolean supportSparse, boolean supportSnapshot, String fileExtension) {
            this.thinProvisioned = thinProvisioned;
            this.supportSparse = supportSparse;
            this.supportSnapshot = supportSnapshot;
            this.fileExtension = fileExtension;
        }

        public boolean isThinProvisioned() {
            return thinProvisioned;
        }

        public boolean supportsSparse() {
            return supportSparse;
        }

        public boolean supportSnapshot() {
            return supportSnapshot;
        }

        public String getFileExtension() {
            if (fileExtension == null)
                return toString().toLowerCase();

            return fileExtension;
        }
        
    }

    public static enum FileSystem {
        Unknown,
        ext3,
        ntfs,
        fat,
        fat32,
        ext2,
        ext4,
        cdfs,
        hpfs,
        ufs,
        hfs,
        hfsp
    }

    public static enum TemplateType {
        ROUTING, // Router template
        SYSTEM, /* routing, system vm template */
        BUILTIN, /* buildin template */
        PERHOST, /* every host has this template, don't need to install it in secondary storage */
        USER /* User supplied template/iso */
    }

    public static enum StoragePoolType {
        Filesystem(false), // local directory
        NetworkFilesystem(true), // NFS
        IscsiLUN(true), // shared LUN, with a clusterfs overlay
        Iscsi(true), // for e.g., ZFS Comstar
        ISO(false), // for iso image
        LVM(false), // XenServer local LVM SR
        CLVM(true),
        RBD(true), // http://libvirt.org/storage.html#StorageBackendRBD
        SharedMountPoint(true),
        VMFS(true), // VMware VMFS storage
        PreSetup(true), // for XenServer, Storage Pool is set up by customers.
        EXT(false), // XenServer local EXT SR
        OCFS2(true),
        SMB(true);

        boolean shared;

        StoragePoolType(boolean shared) {
            this.shared = shared;
        }

        public boolean isShared() {
            return shared;
        }
    }

    public static List<StoragePoolType> getNonSharedStoragePoolTypes() {
        List<StoragePoolType> nonSharedStoragePoolTypes = new ArrayList<StoragePoolType>();
        for (StoragePoolType storagePoolType : StoragePoolType.values()) {
            if (!storagePoolType.isShared()) {
                nonSharedStoragePoolTypes.add(storagePoolType);
            }
        }
        return nonSharedStoragePoolTypes;

    }

    public static enum StorageResourceType {
        STORAGE_POOL, STORAGE_HOST, SECONDARY_STORAGE, LOCAL_SECONDARY_STORAGE
    }
}
