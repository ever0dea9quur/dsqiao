package memory;

import transformer.Transformer;

import java.util.ArrayList;

/**
 * 内存抽象类
 */
public class Memory {

    /*
     * ------------------------------------------
     *            |  Segment=true | Segment=false
     * ------------------------------------------
     * Page=true  |     段页式    |    不存在
     * ------------------------------------------
     * Page=false |    只有分段   |   实地址模式
     * ------------------------------------------
     *
     * 请实现三种模式下的存储管理方案：
     * 实模式：
     * 		无需管理，该情况下不好判断数据是否已经加载到内存(除非给每个字节建立有效位)，干脆每次都重新读Disk(地址空间不会超过1 MB)
     * 分段：
     *      最先适应 -> 空间不足则判断总剩余空间是否足够 -> 足够则进行碎片整理，将内存数据压缩
     * -> 不足则采用最近使用算法LRU直到总剩余空间足够 -> 碎片整理
     * 段页：
     * 		如果数据段已经在内存，使用全关联映射+LRU加载物理页框；如果数据段不在内存，先按照分段模式进行管理，分配的段长度为数据段包含的总物理页框数/2，再将物理页框加载到内存
     */
    public static boolean SEGMENT = false;

    public static boolean PAGE = false;

    public static int MEM_SIZE_B = 32 * 1024 * 1024;      // 主存大小 32 MB
    // 32*1024*1024算出来是32M，故这32M应该是32M个最小可寻址单元，每个单元存储1B，所以注释说主存在校32MB。32M个最小可寻址单元只要25位就可以了

    public static int PAGE_SIZE_B = 1 * 1024;      // 页大小 1 KB，页内偏移10位
    public static ArrayList<SegDescriptor> segTbl = new ArrayList<>(); // 改private为public
    public static PageItem[] pageTbl = new PageItem[Disk.DISK_SIZE_B / Memory.PAGE_SIZE_B]; // 页表大小为2^17  128K // 那么虚页号应该有17位啊 // 改private为public
    private static char[] memory = new char[MEM_SIZE_B]; // 一个char占据一个字节的空间，没毛病
    private static ReversedPageItem[] reversedPageTbl = new ReversedPageItem[Memory.MEM_SIZE_B / Memory.PAGE_SIZE_B]; // 反向页表大小为2^15   32K
    private static Memory memoryInstance = new Memory();
    Transformer t = new Transformer();

    private Memory() {
    }

    public static Memory getMemory() {
        return memoryInstance;
    }

    /**
     * 分段开启的情况下，read方法不允许一次性读取两个段的内容，但是可以一次性读取单个段内多页的内容
     * 注意， read方法应该在load方法被调用之后调用，即read方法的目标页(如果开启分页)都是合法的
     *
     * @param eip 32位物理地址
     * @param len 读取数据的长度
     * @return 内存中的数据
     */
    public char[] read(String eip, int len) {
        // TODO 读取数据

        // 实模式下
        if (!PAGE && !SEGMENT) {
            return Disk.getDisk().read(eip, len);
        }

        // 分段模式下
        else if (!PAGE) {
            int baseAddr = Integer.parseInt(t.binaryToInt(eip));
            char[] data = new char[len];
            System.arraycopy(memory, baseAddr, data, 0, len);
            return data;
        }

        // 段页式下
        else {
            int baseAddr = Integer.parseInt(t.binaryToInt(eip));
            char[] data = new char[len];
            System.arraycopy(memory, baseAddr, data, 0, len);
            return data;
        }
    }

    public void write(String eip, int len, char[] data) {
        // 通知Cache缓存失效
        // 本作业只要求读数据，不要求写数据，因此不存在程序修改数据导致Cache修改 -> Mem修改 -> Disk修改等一系列write back/write through操作，
        //     write方法只用于测试用例中的下层存储修改数据导致上层存储数据失效，Disk.write同理
//        Cache.getCache().invalid(eip, len);
        // 更新数据
        int start = Integer.parseInt(new Transformer().binaryToInt(eip));
        for (int ptr = 0; ptr < len; ptr++) {
            memory[start + ptr] = data[ptr];
        }
    }


    /*************************************************以下为数据结构和测试用例使用的接口*************************************************/

    /**
     * 强制创建一个段描述符，指向指定的物理地址，以便测试用例可以直接修改Disk，
     * 而不用创建一个模拟进程，自上而下进行修改，也不用实现内存分配策略
     * 此方法仅被测试用例使用，而且使用时需小心，此方法不会判断多个段描述符对应的物理存储区间是否重叠
     *
     * @param segSelector 新添加到段表项的索引，int类型
     * @param eip         32-bits,对应段表项中的base
     * @param len         32-bits,其后31位对应段的长度（那第一位是用来干嘛的？
     * @param isValid     标识段是否在内存中
     * @param disk_base   32-bits，对应段表项中段disk_base,即段在磁盘中存储段物理位置
     */
    // 这个方法就是向段表项中增加一项
    public void alloc_seg_force(int segSelector, String eip, int len, boolean isValid, String disk_base) {
        SegDescriptor sd = new SegDescriptor(); // 新的段表项
        Transformer t = new Transformer();
        sd.setDisk(disk_base.toCharArray());
        sd.setBase(eip.toCharArray());
        sd.setLimit(t.intToBinary(String.valueOf(len)).substring(1, 32).toCharArray());
        sd.setValidBit(isValid);
        Memory.segTbl.add(segSelector, sd); // 将新的段表项添加到段表中，segSelector是索引
    }

    /**
     * 清空段表页表，用于测试用例
     */
    public void clear() {
        segTbl = new ArrayList<>();
        for (PageItem pItem : pageTbl) {
            if (pItem != null) {
                pItem.isInMem = false;
            }
        }
    }

    /**
     * 强制使段/页失效，仅用于测试用例
     *
     * @param segNO
     * @param pageNO
     */
    public void invalid(int segNO, int pageNO) {
        if (segNO >= 0) {
            segTbl.get(segNO).validBit = false;
        }
        if (Memory.PAGE) {
            if (pageNO >= 0) {
                pageTbl(pageNO).setInMem(false);
            }
        }
    }

    public PageItem pageTbl(int index) { // private改为public
        if (pageTbl[index] == null) {
            pageTbl[index] = new PageItem();
            return pageTbl[index];
        } else {
            return pageTbl[index];
        }
    }


    ReversedPageItem reversedPageTbl(int index) {
        if (reversedPageTbl[index] == null) {
            reversedPageTbl[index] = new ReversedPageItem();
            return reversedPageTbl[index];
        } else {
            return reversedPageTbl[index];
        }
    }


    /**
     * 理论上应该为Memory分配出一定的系统保留空间用于存放段表和页表，并计算出每个段表和页表所占的字节长度，通过地址访问
     * 不过考虑到Java特性，在此作业的实现中为了简化难度，全部的32M内存都用于存放数据(代码段)，段表和页表直接用独立的数据结构表示，不占用"内存空间"
     * 除此之外，理论上每个进程都会有对应的段表和页表，作业中则假设系统只有一个进程，因此段表和页表也只有一个，不需要再根据进程号选择相应的表
     */
    /**
     * 段选择符理论长度为64-bits，包括32-bits基地址和20-bits的限长(1 MB)，为了测试用例填充内存方便，未被使用的11-bits数据被添加到限长，即作业中限长为31-bits
     */
    // 段描述符，实际上就是段表项
    public class SegDescriptor { // 将private改为public
        // 段基址在缺段中断发生时可能会产生变化，内存重新为段分配内存基址
        private char[] base = new char[32];  // 32位基地址

        private char[] limit = new char[31]; // 31位限长，表示段在内存中的长度

        private boolean validBit = false;    // 有效位,为true表示被占用（段已在内存中），为false表示空闲（不在内存中）

        private long timeStamp = 0l;

        // 段在物理磁盘中的存储位置，真实段描述符里不包含此字段，本作业规定，段在磁盘中连续存储，并且磁盘中的存储位置不会发生变化
        private char[] disk_base = new char[32];

        public SegDescriptor() {
            timeStamp = System.currentTimeMillis();
        }

        public char[] getBase() {
            return base;
        }

        public void setBase(char[] base) {
            this.base = base;
        }

        public char[] getDisk() {
            return disk_base;
        }

        public void setDisk(char[] base) {
            this.disk_base = base;
        }

        public char[] getLimit() {
            return limit;
        }

        public void setLimit(char[] limit) {
            this.limit = limit;
        }

        public boolean isValidBit() {
            return validBit;
        }

        public void setValidBit(boolean validBit) {
            this.validBit = validBit;
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public void updateTimeStamp() {
            this.timeStamp = System.currentTimeMillis();
        }
    }


    /**
     * 页表项为长度为20-bits的页框号
     * 页表项的索引：虚拟页号
     * CPU通过虚拟页号算出index，寻址到对应的页表项，取出相应的frameNumber
     */
    public class PageItem { // 改private 为public

        private char[] frameAddr;

        public boolean isInMem = false; // 改private为public

        public char[] getFrameAddr() {
            return frameAddr;
        }

        public void setFrameAddr(char[] frameAddr) {
            this.frameAddr = frameAddr;
        }

        public boolean isInMem() {
            return isInMem;
        }

        public void setInMem(boolean inMem) {
            isInMem = inMem;
        }

    }

    /**
     * 反向页表 32 * 1024 * 1024 / 1 * 1024 = 32 * 1024 = 32 KB 个反向页表项
     */
    private class ReversedPageItem {

        private boolean isValid = false;    // false表示不在内存，true表示在内存中

        private int vPageNO = -1;           // 虚页页号

        private long timeStamp = System.currentTimeMillis();

        public long getTimeStamp() {
            return this.timeStamp;
        }

        public void updateTimeStamp() {
            this.timeStamp = System.currentTimeMillis();
        }

    }

}