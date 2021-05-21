package cpu;

import memory.Disk;
import memory.Memory;
import memory.Memory.SegDescriptor;
import memory.Memory.PageItem;
import transformer.Transformer;

import java.util.Arrays;


/**
 * MMU接收一个48-bits的逻辑地址，并最终将其转换成32-bits的物理地址
 *
 * Memory.SEGMENT和Memory.PAGE标志用于表示是否开启分段和分页。
 * 实际上在机器里从实模式进入保护模式后分段一定会保持开启(即使实模式也会使用段寄存器)，因此一共只要实现三种模式的内存管理即可：实模式、只有分段、段页式
 * 所有模式的物理地址都采用32-bits，实模式的物理地址高位补0
 *
 * 大致流程(仅供参考)：
 * 		1. 逻辑地址：总长度48-bits(16-bits段选择符+32位段内偏移)
 * 		2. 段选择符高13-bits表示段描述符的索引，低3-bits本作业不使用
 * 		3. 通过段选择符查询段表，获得段描述符，包括32-bits的基地址、31-bits的限长、1-bit的有效位(判断段是否被加载到内存或者失效)
 * 	 		3.1 如果分页未启用且段未加载/失效，则将段从磁盘读取到内存(分段下根据段描述符中的32-bits磁盘基址，段页式下根据虚页号)
 * 		4. 根据基地址和段内偏移计算线性地址(32-bits，包括20-bits虚页页号和12-bits页内偏移) // 为什么页内偏移有12位？页大小为1024 = 2**10
 * 		5. 通过虚页页号查询页表，并获得20-bits的页框号和1-bit标志位(记录该页是否在内存中或者失效)
 * 			5.1 如果页不在内存，则将页从磁盘读取到内存
 * 		6. 页框号与页内偏移组合成物理地址，根据物理地址和数据长度读Cache
 * 	段页式系统中，逻辑地址仍然由段号+段内偏移组成；段内偏移可以看作是段中段一个页号和页偏移量——《操作系统》
 */
public class MMU {

	private static MMU mmuInstance = new MMU();

	private MMU() {}

	public static MMU getMMU() {
		return mmuInstance;
	}

	Memory memory = Memory.getMemory();

	Transformer t = new Transformer();

	/**
	 * 地址转换
	 * @param logicAddr 48-bits逻辑地址。实模式和分段模式下，磁盘物理地址==内存物理地址，段页式下，磁盘物理地址==虚页号 * 页框大小 + 偏移量
	 * @param length 读取数据的长度
	 * @return 内存中的数据
	 */
	public char[] read(String logicAddr, int length) {
		String linearAddr;          // 32位线性地址
		String physicalAddr = "";   // 32位物理地址
		// TODO 加载数据 + 地址转换

		// 实模式下
		if (!Memory.PAGE && !Memory.SEGMENT){
			physicalAddr = logicAddr.substring(16);
		}

		// 分段模式下
		else if (!Memory.PAGE){
			int descriptorIndex = Integer.parseInt(t.binaryToInt(logicAddr.substring(0, 13)));
			SegDescriptor descriptor = Memory.segTbl.get(descriptorIndex);
			String diskBase = String.valueOf(descriptor.getDisk());
			physicalAddr = diskBase;
			if (!descriptor.isValidBit()){
				int l = Integer.parseInt(t.binaryToInt(String.valueOf(descriptor.getLimit())));
				char[] seg = Disk.getDisk().read(diskBase, l);
				memory.write(physicalAddr, l, seg);
			}
		}

		// 段页式模式下
		else {

			// 通过段号查询段
			int descriptorIndex = Integer.parseInt(t.binaryToInt(logicAddr.substring(0, 13)));
			SegDescriptor descriptor = Memory.segTbl.get(descriptorIndex);

			// 段的基址+段内偏移得到线性地址
			int base = Integer.parseInt(t.binaryToInt(String.valueOf(descriptor.getBase())));
			int offset = Integer.parseInt(t.binaryToInt(logicAddr.substring(16)));
			linearAddr = t.intToBinary(String.valueOf(base+offset));

			// 线性地址前20位为页号，后12位为页内偏移
			int pageNum = Integer.parseInt(t.binaryToInt(linearAddr.substring(0, 20)));
			int offset2 = Integer.parseInt(t.binaryToInt(linearAddr.substring(20)));

			char[] data = Disk.getDisk().read(t.intToBinary(String.valueOf(pageNum * 1024 + offset2)), length);
			memory.write(physicalAddr, length, data);

		}
		return memory.read(physicalAddr, length);
	}

}
