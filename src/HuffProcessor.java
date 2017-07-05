import java.util.PriorityQueue;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
	   int[] counts = readForCounts(in);
	   TreeNode root = makeTreeFromCounts(counts);
	   String[] codings = makeCodingsFromTree(root);
	   out.writeBits(BITS_PER_INT, HUFF_TREE);
	   writeHeader(root, out);
	   in.reset();
	   writeCompressedBits(in, codings, out);
	}
	public int[] readForCounts(BitInputStream in){
		int[] dict = new int[256];
		while(true){
			int nums = in.readBits(BITS_PER_WORD);
			if(nums==-1)break;
			dict[nums]++;
		}
		return dict;
	}
	public TreeNode makeTreeFromCounts(int[] read){
		PriorityQueue<TreeNode> pq = new PriorityQueue<>();
		for(int i=0;i<read.length;i++){
			if(read[i]>0){
			TreeNode temp = new TreeNode(i, read[i]);
			pq.add(temp);
			}
		}
		TreeNode eof = new TreeNode(PSEUDO_EOF, 1);
		pq.add(eof);
		while (pq.size() > 1) {
		    TreeNode left = pq.remove();
		    TreeNode right = pq.remove();
		    TreeNode t = new TreeNode(0,left.myWeight + right.myWeight,left,right);
		    pq.add(t);
		}
		TreeNode root = pq.remove();
		return root;
	}
	public String[] makeCodingsFromTree(TreeNode root){
		String[] val = new String[257];
		makeCodingsHelper(root, "", val);
		return val;
	}
	public void makeCodingsHelper(TreeNode root, String path, String val[]){
		if(root.myLeft==null && root.myRight==null){
			val[root.myValue] = path;
			return;
		}
		else{
			makeCodingsHelper(root.myLeft, path + "0", val);
			makeCodingsHelper(root.myRight, path + "1", val);
		}
	}
	public void writeHeader(TreeNode root, BitOutputStream out){
		if(root.myLeft==null && root.myRight==null){
			out.writeBits(1, 1);
			out.writeBits(9, root.myValue);
			return;
		}
		out.writeBits(1, 0);
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}
	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out){
		while(true){
			int bits = in.readBits(BITS_PER_WORD);
			if(bits==-1)break;
			out.writeBits(codings[bits].length(), Integer.parseInt(codings[bits], 2));
		}
		out.writeBits(codings[256].length(), Integer.parseInt(codings[256],2));
	}

	public void decompress(BitInputStream in, BitOutputStream out) throws HuffException{
	    int magic = in.readBits(BITS_PER_INT);
	    if(magic == HUFF_TREE || magic == HUFF_NUMBER){
	    TreeNode root = readTreeHeader (in);
	    readCompressedBits(root, in, out);
	    }
	    else{
	    	throw new HuffException("Incorrect magic number");
	    }
}
	public void readCompressedBits(TreeNode root,BitInputStream in,BitOutputStream out){

		TreeNode init = root;
		while(true){
			int bits = in.readBits(1);
			if(bits == -1){
				System.err.println("Issue with the bits");
				break;
			}
			else{
				if(bits==0)root = root.myLeft;
				else{root=root.myRight;}
				if(root.myLeft==null && root.myRight==null){
					if(root.myValue==PSEUDO_EOF){
						break;
					}
					else{
						out.writeBits(BITS_PER_WORD, root.myValue);
						root = init;
					}
				}
			}
		}
	}
	public TreeNode readTreeHeader(BitInputStream in){
		int bits = in.readBits(1);
		TreeNode node;
		if(bits==0){
			TreeNode left = readTreeHeader(in);
			TreeNode right = readTreeHeader(in);
			node = new TreeNode(0, left.myWeight+right.myWeight, left, right);
		}
		else{
			bits=in.readBits(9);
			node= new TreeNode(bits, 1);
		}
		return node;
	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
}