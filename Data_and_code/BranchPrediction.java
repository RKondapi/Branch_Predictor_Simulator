import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
class BranchPrediction {
	public String predictorType;
	public int m1;
	public int m2;
	public int k;
	public int n;
	public String traceFile;
	public List<String> branchInstructionList = new ArrayList<String>();
	public String[] takenOrNot;
	public int branches;
	public String[] branchAddress;
	public int[] branchAddressPartingIndB;
	public int[] branchAddressPartingIndG;
	public int[] branchAddressPartingIndH;
	public int[] predictionTableB;
	public int[] predictionTableG;
	public int[] chooserTable;
	public String[] inputs;
	public int mispredictions=0;
	public char[] historyReg;
	public int predictions;
	public char predictedBranchB;
	public char predictedBranchG;
	public boolean bimodalFlag=true;
	public boolean gshareFlag=true;
	public String[] branchAddressPartingN;
	public String[] branchAddressPartingM;
	public void readTraceFile()
	{
		File traceFileRead = new File("./"+traceFile);
		List<String> instructions;
		String binaryVal;
		//To check if the file is valid performing exception handling. If it is valid get all the instructions per each line
		try 
		{
			int eachInst=0;
			instructions=Files.readAllLines(Paths.get("./"+traceFile));
			branchAddress=new String[instructions.size()];
			takenOrNot=new String[instructions.size()];
		    for(String eachInstruction:instructions)
		    {
		    	binaryVal=hexToBinary(eachInstruction.split(" ")[0],24);
				branchAddress[eachInst]=binaryToHex(binaryVal.substring(0,binaryVal.length()-2));
				takenOrNot[eachInst]=eachInstruction.split(" ")[1];
				eachInst++;
		    }
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		if(predictorType.equalsIgnoreCase("bimodal"))
			branchAddressPartingIndB=new int[branchAddress.length];
		if(predictorType.equalsIgnoreCase("gshare"))
		{
			branchAddressPartingIndG=new int[branchAddress.length];
			branchAddressPartingN=new String[branchAddress.length];
			branchAddressPartingM=new String[branchAddress.length];
		}
		if(predictorType.equalsIgnoreCase("hybrid"))
		{
			branchAddressPartingN=new String[branchAddress.length];
			branchAddressPartingM=new String[branchAddress.length];
			branchAddressPartingIndB=new int[branchAddress.length];
			branchAddressPartingIndG=new int[branchAddress.length];
			branchAddressPartingIndH=new int[branchAddress.length];
		}
		predictions=branchAddress.length;
	}
	//Bimodal logic for each address as an argument
	public void bimodalLogic(int i)
	{
		String binaryVal;
		int addLen;
		binaryVal=hexToBinary(branchAddress[i],22);
		addLen=binaryVal.length();
		branchAddressPartingIndB[i]=Integer.valueOf(binaryToDec(binaryVal.substring(addLen-m2,addLen)));
		String op;
		int indexBit;
		//Get the index bit of first instruction
		indexBit=Integer.valueOf(branchAddressPartingIndB[i]);
		//Get the operation (t or n) for the first instruction
		op=takenOrNot[i];
		//Saving the predictions of bimodal to a variable used for hybrid in updating chooser table
		if(predictorType.equalsIgnoreCase("hybrid"))
		{
			if(predictionTableB[indexBit]<4 && predictionTableB[indexBit]>=0)
				predictedBranchB='n';
			else if(predictionTableB[indexBit]>=4 && predictionTableB[indexBit]<=7)
				predictedBranchB='t';
		}
		//Condition to update prediction table if the operation is taken
		if(op.equalsIgnoreCase("t"))
		{
			if(bimodalFlag)
				updatePTableBimodalIfT(indexBit);
		}
		//Condition to update prediction table if the operation is not taken
		else if(op.equalsIgnoreCase("n"))
		{
			if(bimodalFlag)
				updatePTableBimodalIfN(indexBit);
		}
	}
	//This is where the bimodal starts
	public void bimodalPredictor()
	{
		readTraceFile();
		for(int i=0;i<branchAddressPartingIndB.length;i++)
		{
			bimodalLogic(i);
		}
	}
	//This function contains gshare logic for each address as an argument
	public void gshareLogic(int i)
	{
		int addLen;
		String binaryVal;
		int indexBit;
		String op;
		binaryVal=hexToBinary(branchAddress[i],22);
		addLen=binaryVal.length();
		branchAddressPartingM[i]=binaryVal.substring(addLen-m1,addLen-n);
		branchAddressPartingN[i]=binaryVal.substring(addLen-n,addLen);
		branchAddressPartingN[i]=xorOp(branchAddressPartingN[i]);
		branchAddressPartingM[i]=branchAddressPartingM[i]+branchAddressPartingN[i];
		branchAddressPartingIndG[i]=Integer.valueOf(binaryToDec(branchAddressPartingM[i]));
		//Get the index bit of first instruction
		indexBit=Integer.valueOf(branchAddressPartingIndG[i]);
		//Get the operation (t or n) for the first instruction
		op=takenOrNot[i];
		//Saving the predictions of gshare to a variable used for hybrid in updating chooser table
		if(predictorType.equalsIgnoreCase("hybrid"))
		{
			if(predictionTableG[indexBit]<4 && predictionTableG[indexBit]>=0)
				predictedBranchG='n';
			else if(predictionTableG[indexBit]>=4 && predictionTableG[indexBit]<=7)
				predictedBranchG='t';
		}
		//Condition to update prediction table if the operation is taken
		if(op.equalsIgnoreCase("t"))
		{
			if(gshareFlag)
				updatePTableGshareIfT(indexBit);
		}
		//Condition to update prediction table if the operation is not taken
		else if(op.equalsIgnoreCase("n"))
		{
			if(gshareFlag)
				updatePTableGshareIfN(indexBit);
		}
	}
	//This is where the gshare starts
	public void gsharePredictor()
	{
		readTraceFile();
		for(int i=0;i<branchAddress.length;i++)
		{
			gshareLogic(i);
		}
	}
	//This function contains logic for hybrid predictor
	public void hybridPredictor()
	{
		readTraceFile();
		String binaryVal;
		int addLen;
		String op;
		int indexBit;
		for(int i=0;i<branchAddress.length;i++)
		{
			binaryVal=hexToBinary(branchAddress[i],22);
			addLen=binaryVal.length();
			branchAddressPartingIndB[i]=Integer.valueOf(binaryToDec(binaryVal.substring(addLen-k,addLen)));
			//Get the index bit of first instruction
			indexBit=Integer.valueOf(branchAddressPartingIndB[i]);
			//Get the operation (t or n) for the first instruction
			op=takenOrNot[i];
			//Condition to check if chooser table content is greater than equal than 2, if so go to gshare logic
			if(chooserTable[indexBit]>=2)
			{
				gshareFlag=true;
				bimodalFlag=false;
				gshareLogic(i);
				bimodalLogic(i);
			}
			//Condition to check if chooser table content is less than 2, if so go to bimodal logic
			else if(chooserTable[indexBit]<2)
			{
				gshareFlag=false;
				bimodalFlag=true;
				gshareLogic(i);
				bimodalLogic(i);
			}
			//Update chooser table contents, increment if bimodal predicted incorrectly
			if(op.equalsIgnoreCase(String.valueOf(predictedBranchG)) && !op.equalsIgnoreCase(String.valueOf(predictedBranchB)))
			{
				if(chooserTable[indexBit]<3 && chooserTable[indexBit]>=0)
					chooserTable[indexBit]++;
			}
			//Decrement if gshare predicted incorrectly
			if(!op.equalsIgnoreCase(String.valueOf(predictedBranchG)) && op.equalsIgnoreCase(String.valueOf(predictedBranchB)))
			{
				if(chooserTable[indexBit]<=3 && chooserTable[indexBit]>0)
					chooserTable[indexBit]--;
			}
		}
	}
	//This function is to display all the results
	public void displayRes()
	{
		double mispredictionRate=((double)(mispredictions)/(predictions))*100;
		DecimalFormat df=new DecimalFormat("#0.00");
		df.setRoundingMode(RoundingMode.HALF_UP);
		System.out.println("COMMAND  ");
		System.out.print("./sim "); 
		for(int i=0;i<inputs.length;i++)
			System.out.print(inputs[i]+" ");
		System.out.print("\r\n" + 
				"OUTPUT\r\n" + 
				"number of predictions:		"+predictions+"\r\n" + 
				"number of mispredictions:	"+mispredictions+"\r\n" + 
				"misprediction rate:		"+df.format(mispredictionRate)+"%\n");
		if(predictorType.equalsIgnoreCase("bimodal"))
		{
			displayPTableB();
		}
		else if(predictorType.equalsIgnoreCase("gshare"))
		{
			displayPTableG();
		}
		else if(predictorType.equalsIgnoreCase("hybrid"))
		{
			System.out.println("FINAL CHOOSER CONTENTS");
			for(int i=0;i<chooserTable.length;i++)
				System.out.println(i+"	"+chooserTable[i]);
			displayPTableG();
			displayPTableB();
		}
	}
	//This function is to display contents in prediction table for gshare
	public void displayPTableG()
	{
		System.out.println("FINAL GSHARE CONTENTS");
		for(int i=0;i<predictionTableG.length;i++)
			System.out.println(i+"	"+predictionTableG[i]);
	}
	//This function is to display contents in prediction table for bimodal
	public void displayPTableB()
	{
		System.out.println("FINAL BIMODAL CONTENTS");
		for(int i=0;i<predictionTableB.length;i++)
			System.out.println(i+"	"+predictionTableB[i]);
	}
	public BranchPrediction(String[] args) {
		//Declaring all the input arguments needed for branch prediction
		inputs=args;
		predictorType=String.valueOf(args[0]);
		if(predictorType.equalsIgnoreCase("bimodal"))
		{
			m2=Integer.valueOf(args[1]);
			traceFile=String.valueOf(args[2]);
			predictionTableB=new int[(int) Math.pow(2,m2)];
			for(int i=0;i<predictionTableB.length;i++)
				predictionTableB[i]=4;
			bimodalPredictor();
		}
		else if(predictorType.equalsIgnoreCase("gshare"))
		{
			m1=Integer.valueOf(args[1]);
			n=Integer.valueOf(args[2]);
			traceFile=String.valueOf(args[3]);
			historyReg=new char[n];
			for(int i=0;i<historyReg.length;i++)
				historyReg[i]='0';
			predictionTableG=new int[(int) Math.pow(2,m1)];
			for(int i=0;i<predictionTableG.length;i++)
				predictionTableG[i]=4;
			gsharePredictor();
		}
		else if(predictorType.equalsIgnoreCase("hybrid"))
		{
			k=Integer.valueOf(args[1]);
			m1=Integer.valueOf(args[2]);
			n=Integer.valueOf(args[3]);
			m2=Integer.valueOf(args[4]);
			traceFile=String.valueOf(args[5]);
			chooserTable=new int[(int) Math.pow(2,k)];
			for(int i=0;i<chooserTable.length;i++)
				chooserTable[i]=1;
			historyReg=new char[n];
			for(int i=0;i<historyReg.length;i++)
				historyReg[i]='0';
			predictionTableG=new int[(int) Math.pow(2,m1)];
			for(int i=0;i<predictionTableG.length;i++)
				predictionTableG[i]=4;
			predictionTableB=new int[(int) Math.pow(2,m2)];
			for(int i=0;i<predictionTableB.length;i++)
				predictionTableB[i]=4;
			hybridPredictor();
		}
		displayRes();
	}
	//function to perform xor operation
	public String xorOp(String nValSplit)
	{
		String res="";
		char[] charNVal=nValSplit.toCharArray();
		for(int i=0;i<historyReg.length;i++)
			res=res.concat(String.valueOf(Integer.valueOf(charNVal[i])^Integer.valueOf(historyReg[i])));
		return res;
	}
	//Function to update history if predicted T
	public void historyUpdateIfT()
	{
		for(int j=n-1;j>=0;j--)
		{
			if(j>0)
				historyReg[j]=historyReg[j-1];
			else
				historyReg[j]='1';
		}
	}
	//Function to update history if predicted N
	public void historyUpdateIfN()
	{
		for(int j=n-1;j>=0;j--)
		{
			if(j>0)
				historyReg[j]=historyReg[j-1];
			else
				historyReg[j]='0';
		}
	}
	//Function to update prediction table and mispredictions if predicted T for gshare
	public void updatePTableGshareIfT(int indexBit)
	{
		historyUpdateIfT();
		if(predictionTableG[indexBit]<4 && predictionTableG[indexBit]>=0)
			mispredictions++;
		if(predictionTableG[indexBit]<7)
			predictionTableG[indexBit]++;
	}
	//Function to update prediction table and mispredictions if predicted N for gshare
	public void updatePTableGshareIfN(int indexBit)
	{
		historyUpdateIfN();
		if(predictionTableG[indexBit]>=4 && predictionTableG[indexBit]<=7)
			mispredictions++;
		if(predictionTableG[indexBit]>0)
			predictionTableG[indexBit]--;
	}
	//Function to update prediction table and mispredictions if predicted T for bimodal
	public void updatePTableBimodalIfT(int indexBit)
	{
		historyUpdateIfT();
		if(predictionTableB[indexBit]<4 && predictionTableB[indexBit]>=0)
			mispredictions++;
		if(predictionTableB[indexBit]<7)
			predictionTableB[indexBit]++;
	}
	//Function to update prediction table and mispredictions if predicted N for bimodal
	public void updatePTableBimodalIfN(int indexBit)
	{
		historyUpdateIfN();
 		if(predictionTableB[indexBit]>=4 && predictionTableB[indexBit]<=7)
 			mispredictions++;
 		if(predictionTableB[indexBit]>0)
 			predictionTableB[indexBit]--;
	}
	//Function to convert Hexadecimal to Binary 
	public String hexToBinary(String hexCode, int totalBits) 
	{
		return String.format("%"+totalBits+"s",Integer.toBinaryString(Integer.parseInt(hexCode, 16))).replaceAll(" ", "0");
	}
	//Function to convert Binary to Hexadecimal
	public String binaryToHex(String binaryCode) 
	{
		return Integer.toString(Integer.parseInt(binaryCode,2),16);
	}
	//Function to convert Binary to Decimal
	public String binaryToDec(String binaryCode) 
	{
		return Integer.toString(Integer.parseInt(binaryCode,2));
	}
}